package com.reencryptutility.service;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reencryptutility.dto.CryptoManagerRequestDTO;
import com.reencryptutility.dto.CryptoManagerResponseDTO;
import com.reencryptutility.dto.RequestWrapper;
import com.reencryptutility.dto.ResponseWrapper;
import com.reencryptutility.entity.DemographicEntity;
import com.reencryptutility.entity.DocumentEntity;
import com.reencryptutility.repository.DemographicRepository;
import com.reencryptutility.repository.DocumentRepository;
import io.mosip.commons.khazana.exception.ObjectStoreAdapterException;
import io.mosip.commons.khazana.spi.ObjectStoreAdapter;
import io.mosip.kernel.core.util.HMACUtils;
import net.logstash.logback.encoder.org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static io.mosip.commons.khazana.constant.KhazanaErrorCodes.OBJECT_STORE_NOT_ACCESSIBLE;

@Component
public class ReEncrypt {

    Logger logger = org.slf4j.LoggerFactory.getLogger(ReEncrypt.class);

    @Autowired
    RestTemplate restTemplate;

    @Value("${cryptoResource.url}")
    public String cryptoResourceUrl;

    @Value("${appId}")
    public String appId;

    @Value("${clientId}")
    public String clientId;

    @Value("${secretKey}")
    public String secretKey;

    @Value("${decryptBaseUrl}")
    public String decryptBaseUrl;

    @Value("${encryptBaseUrl}")
    public String encryptBaseUrl;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DemographicRepository reEncryptRepository;

    @Autowired
    private DemographicRepository demographicRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Qualifier("S3Adapter")
    @Autowired
    private ObjectStoreAdapter objectStore;

    @Value("${mosip.kernel.objectstore.account-name}")
    private String objectStoreAccountName;
    @Value("${isNewDatabase:true}")
    private boolean isNewDatabase;
    @Value("${object.store.s3.accesskey:accesskey:accesskey}")
    private String accessKey;
    @Value("${object.store.s3.secretkey:secretkey:secretkey}")
    private String objectStoreSecretKey;
    @Value("${object.store.s3.url:null}")
    private String url;
    @Value("${destinationObjectStore.s3.url}")
    private String destinationObjectStoreUrl;
    @Value("${destinationObjectStore.s3.access-key}")
    private String destinationObjectStoreAccessKey;
    @Value("${destinationObjectStore.s3.secret-key}")
    private String destinationObjectStoreSecretKey;
    @Value("${destinationObjectStore.s3.region:null}")
    private String region;
    @Value("${destinationObjectStore.s3.readlimit:10000000}")
    private int readlimit;
    @Value("${destinationObjectStore.connection.max.retry:5}")
    private int maxRetry;
    @Value("${object.store.max.connection:20}")
    private int maxConnection;
    @Value("${object.store.s3.use.account.as.bucketname:false}")
    private boolean useAccountAsBucketname;

    private int retry = 0;
    private AmazonS3 connection = null;

    private static final String SUFFIX = "/";

    String token = "";

    public int row;

    public int successFullRow;

    public List<DemographicEntity> demographicEntityList = new ArrayList<>();

    public List<DocumentEntity> documentEntityLists = new ArrayList<>();

    public ReEncrypt(ObjectMapper mapper, DemographicRepository reEncryptRepository) {
        this.mapper = mapper;
        this.reEncryptRepository = reEncryptRepository;
    }

    public void generateToken(String url) {
        RequestWrapper<ObjectNode> requestWrapper = new RequestWrapper<>();
        ObjectNode request = mapper.createObjectNode();
        request.put("appId", appId);
        request.put("clientId", clientId);
        request.put("secretKey", secretKey);
        requestWrapper.setRequest(request);
        ResponseEntity<ResponseWrapper> response = restTemplate.postForEntity(url + "/v1/authmanager/authenticate/clientidsecretkey", requestWrapper,
                ResponseWrapper.class);
        token = response.getHeaders().getFirst("authorization");
        restTemplate.setInterceptors(Collections.singletonList(new ClientHttpRequestInterceptor() {

            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                    throws java.io.IOException {
                request.getHeaders().add(HttpHeaders.COOKIE, "Authorization=" + token);
                return execution.execute(request, body);
            }
        }));
    }

    public byte[] decrypt(byte[] originalInput, LocalDateTime localDateTime, String decryptBaseUrl) throws Exception {
        logger.info("In decrypt method of CryptoUtil service ");
        ResponseEntity<ResponseWrapper<CryptoManagerResponseDTO>> response = null;
        byte[] decodedBytes = null;
        generateToken(decryptBaseUrl);
        try {
            CryptoManagerRequestDTO dto = new CryptoManagerRequestDTO();
            dto.setApplicationId("REGISTRATION");
            dto.setData(new String(originalInput, StandardCharsets.UTF_8));
            dto.setReferenceId("");
            dto.setTimeStamp(localDateTime);
            RequestWrapper<CryptoManagerRequestDTO> requestKernel = new RequestWrapper<>();
            requestKernel.setRequest(dto);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RequestWrapper<CryptoManagerRequestDTO>> request = new HttpEntity<>(requestKernel, headers);
            logger.info("In decrypt method of CryptoUtil service cryptoResourceUrl: " + cryptoResourceUrl + "/decrypt");
            response = restTemplate.exchange(cryptoResourceUrl + "/decrypt", HttpMethod.POST, request,
                    new ParameterizedTypeReference<ResponseWrapper<CryptoManagerResponseDTO>>() {
                    });
            logger.info("myresponse\n" + response.getBody().getResponse().getData().getBytes(StandardCharsets.UTF_8));
            decodedBytes = response.getBody().getResponse().getData().getBytes();
        } catch (Exception ex) {
            logger.error("Error in decrypt method of CryptoUtil service " + ex.getMessage());
        }
        return decodedBytes;
    }

    public byte[] encrypt(byte[] originalInput, LocalDateTime localDateTime, String encryptBaseUrl) {
        logger.info("sessionId", "idType", "id", "In encrypt method of CryptoUtil service ");
        generateToken(encryptBaseUrl);
        ResponseEntity<ResponseWrapper<CryptoManagerResponseDTO>> response = null;
        byte[] encryptedBytes = null;
        try {
            CryptoManagerRequestDTO dto = new CryptoManagerRequestDTO();
            dto.setApplicationId("PRE_REGISTRATION");
            dto.setData(new String(originalInput, StandardCharsets.UTF_8));
            dto.setReferenceId("INDIVIDUAL");
            dto.setTimeStamp(localDateTime);
            dto.setPrependThumbprint(false);
            RequestWrapper<CryptoManagerRequestDTO> requestKernel = new RequestWrapper<>();
            requestKernel.setRequest(dto);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<RequestWrapper<CryptoManagerRequestDTO>> request = new HttpEntity<>(requestKernel, headers);
            logger.info("sessionId", "idType", "id",
                    "In encrypt method of CryptoUtil service cryptoResourceUrl: " + "/encrypt");
            response = restTemplate.exchange(encryptBaseUrl + "/v1/keymanager/encrypt", HttpMethod.POST, request,
                    new ParameterizedTypeReference<ResponseWrapper<CryptoManagerResponseDTO>>() {
                    });
            encryptedBytes = response.getBody().getResponse().getData().getBytes();
        } catch (Exception ex) {
            logger.error("sessionId", "idType", "id", "Error in encrypt method of CryptoUtil service " + ex.getMessage());
        }
        return encryptedBytes;
    }

    public static String hashUtill(byte[] bytes) {
        return HMACUtils.digestAsPlainText(HMACUtils.generateHash(bytes));
    }

    public void start() throws Exception {
        DatabaseThreadContext.setCurrentDatabase(Database.PRIMARY);
        logger.info("sessionId", "idType", "id", "In start method of CryptoUtil service ");

        List<DemographicEntity> applicantDemographic = demographicRepository.findAll();
        reEncryptData(applicantDemographic);
        List<DocumentEntity> documentEntityList = documentRepository.findAll();
        reEncryptOldDocument(documentEntityList);
        logger.info("size of list"+documentEntityLists.size());
        if(isNewDatabase) {
            InsertDataInNewDatabase();
        }
    }

    private AmazonS3 getConnection(String bucketName) {
        if (connection != null)
            return connection;

        try {
            AWSCredentials awsCredentials = new BasicAWSCredentials(destinationObjectStoreAccessKey, destinationObjectStoreSecretKey);
            connection = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                    .enablePathStyleAccess().withClientConfiguration(new ClientConfiguration().withMaxConnections(maxConnection)
                            .withMaxErrorRetry(maxRetry))
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(destinationObjectStoreUrl, region)).build();
            // test connection once before returning it
            connection.doesBucketExistV2(bucketName);
            // reset retry after every successful connection so that in case of failure it starts from zero.
            retry = 0;
        } catch (Exception e) {
            if (retry >= maxRetry) {
                // reset the connection and retry count
                retry = 0;
                connection = null;
                logger.error("Maximum retry limit exceeded. Could not obtain connection for "+ bucketName +". Retry count :" + retry, ExceptionUtils.getStackTrace(e));
                throw new ObjectStoreAdapterException(OBJECT_STORE_NOT_ACCESSIBLE.getErrorCode(), OBJECT_STORE_NOT_ACCESSIBLE.getErrorMessage(), e);
            } else {
                connection = null;
                retry = retry + 1;
                logger.error("Exception occured while obtaining connection for "+ bucketName +". Will try again. Retry count : " + retry, ExceptionUtils.getStackTrace(e));
                getConnection(bucketName);
            }
        }
        return connection;
    }

    private void InsertDataInNewDatabase() {
        logger.info("sessionId", "idType", "id", "In InsertDataInNewDatabase method of CryptoUtil service ");
        DatabaseThreadContext.setCurrentDatabase(Database.SECONDARY);
        logger.info("size of list"+demographicEntityList.size());
        logger.info("size of qa=upgrade"+demographicRepository.findAll().size());

       for(DemographicEntity demographicEntity : demographicEntityList) {
           logger.info("demographicEntity prereg id : " + demographicEntity.getPreRegistrationId());
           if(demographicRepository.findBypreRegistrationId(demographicEntity.getPreRegistrationId()) == null) {
               DemographicEntity demographicEntity1 = new DemographicEntity();
               demographicEntity1.setPreRegistrationId(demographicEntity.getPreRegistrationId());
               demographicEntity1.setDemogDetailHash(demographicEntity.getDemogDetailHash());
               demographicEntity1.setEncryptedDateTime(demographicEntity.getEncryptedDateTime());
               demographicEntity1.setApplicantDetailJson(demographicEntity.getApplicantDetailJson());
               demographicEntity1.setStatusCode(demographicEntity.getStatusCode());
               demographicEntity1.setLangCode(demographicEntity.getLangCode());
               demographicEntity1.setCrAppuserId(demographicEntity.getCrAppuserId());
               demographicEntity1.setCreatedBy(demographicEntity.getCreatedBy());
               demographicEntity1.setCreateDateTime(demographicEntity.getCreateDateTime());
               demographicEntity1.setUpdatedBy(demographicEntity.getUpdatedBy());
               demographicEntity1.setUpdateDateTime(demographicEntity.getUpdateDateTime());
               demographicRepository.save(demographicEntity1);
           }
       }
        logger.info("size of list"+documentEntityLists.size());
        for (DocumentEntity documentEntities : documentEntityLists) {
            logger.info("pre-registration-id:-" + documentEntities.getDemographicEntity().getPreRegistrationId());
            logger.info("inside document entity"+ documentEntities.getDocumentId());
            DocumentEntity documentEntity = new DocumentEntity();
            documentEntity.setDemographicEntity(documentEntities.getDemographicEntity());
            documentEntity.setDocId(documentEntities.getDocId());
            documentEntity.setDocumentId(documentEntities.getDocumentId());
            documentEntity.setDocName(documentEntities.getDocName());
            documentEntity.setDocCatCode(documentEntities.getDocCatCode());
            documentEntity.setDocTypeCode(documentEntities.getDocTypeCode());
            documentEntity.setDocFileFormat(documentEntities.getDocFileFormat());
            documentEntity.setDocumentId(documentEntities.getDocumentId());
            documentEntity.setDocHash(documentEntities.getDocHash());
            documentEntity.setEncryptedDateTime(documentEntities.getEncryptedDateTime());
            documentEntity.setStatusCode(documentEntities.getStatusCode());
            documentEntity.setLangCode(documentEntities.getLangCode());
            documentEntity.setCrBy(documentEntities.getCrBy());
            documentEntity.setCrDtime(documentEntities.getCrDtime());
            documentEntity.setUpdBy(documentEntities.getUpdBy());
            documentEntity.setUpdDtime(documentEntities.getUpdDtime());
            documentEntity.setRefNumber(documentEntities.getRefNumber());
            documentRepository.save(documentEntity);
        }
        DatabaseThreadContext.setCurrentDatabase(Database.PRIMARY);
    }

    private void reEncryptOldDocument(List<DocumentEntity> documentEntityList)  {
        logger.info("Total rows:-" + documentEntityList.size());
        int objectStoreFoundCounter=0;
        for (DocumentEntity documentEntities : documentEntityList) {
            logger.info("pre-registration-id:-" + documentEntities.getDemographicEntity().getPreRegistrationId());
            documentEntityList = documentRepository.findByDemographicEntityPreRegistrationId(documentEntities.getDemographicEntity().getPreRegistrationId());
            logger.info("Total rows found in prereg:-" + documentEntityList.size());
            if (documentEntityList != null && !documentEntityList.isEmpty()) {
                logger.info("spcific prereg id:" + documentEntityList.size());
                for (DocumentEntity documentEntity : documentEntityList) {
                    logger.info(documentEntity.getDemographicEntity().getPreRegistrationId());
                    String key = documentEntity.getDocCatCode() + "_" + documentEntity.getDocumentId();
                    try {
                        if (objectStore.exists(objectStoreAccountName, documentEntity.getDemographicEntity().getPreRegistrationId(), null, null, key) == false) {
                            logger.info("key not found in objectstore");
                            continue;
                        }
                        logger.info("key  found in objectstore");
                        InputStream sourcefile = objectStore.getObject(objectStoreAccountName,
                                documentEntity.getDemographicEntity().getPreRegistrationId(), null, null, key);
                        logger.info("sourcefile" + sourcefile);
                        if (sourcefile != null) {
                            objectStoreFoundCounter++;
                            logger.info("sourcefile not null");
                            byte[] bytes = IOUtils.toByteArray(sourcefile);
                            byte[] decryptedBytes =  decrypt(bytes, LocalDateTime.now(), decryptBaseUrl);
                            if (decryptedBytes == null) {
                                logger.info("decryptedBytes is null");
                                continue;
                            }
                            byte[] reEncryptedBytes = encrypt(decryptedBytes, LocalDateTime.now(), encryptBaseUrl);
                            logger.info("bytes:\n" + bytes);
                            logger.info("decryptedBytes:\n" + decryptedBytes);
                            logger.info("reEncryptedBytes:\n" + (reEncryptedBytes));
                            String folderName = documentEntity.getDemographicEntity().getPreRegistrationId();
                            if(isNewDatabase) {
                                AmazonS3 connection = getConnection(folderName);
                                if (!connection.doesBucketExistV2(folderName))
                                    connection.createBucket(folderName);
                                connection.putObject(folderName, key, new ByteArrayInputStream(reEncryptedBytes), null);
                                documentEntity.setDocHash(hashUtill(reEncryptedBytes));
                                documentEntity.setEncryptedDateTime(LocalDateTime.now());
                                logger.info("inside document entity"+ documentEntity.getDocumentId());
                                documentEntityLists.add(documentEntity);
                            }
                            else {
                                objectStore.putObject(objectStoreAccountName, documentEntity.getDemographicEntity().getPreRegistrationId(), null, null, key, new ByteArrayInputStream(reEncryptedBytes));
                            }
                            List<DocumentEntity> currentDocumentEntityList =  documentRepository.findByDemographicEntityPreRegistrationId(documentEntity.getDemographicEntity().getPreRegistrationId());
                            for (DocumentEntity currentDocumentEntity : currentDocumentEntityList) {
                                currentDocumentEntity.setDocHash(hashUtill(reEncryptedBytes));
                                currentDocumentEntity.setEncryptedDateTime(LocalDateTime.now());
                                demographicRepository.save(currentDocumentEntity.getDemographicEntity());
                                documentRepository.save(currentDocumentEntity);
                            }
                        }
                    } catch (Exception e) {
                        logger.info("Exception:- bucket not found");
                        throw new ObjectStoreAdapterException(OBJECT_STORE_NOT_ACCESSIBLE.getErrorCode(), OBJECT_STORE_NOT_ACCESSIBLE.getErrorMessage(), e);
                    }
                    logger.info("DocumentEntity:-" + documentEntity.getDocumentId());
                }
            }
        }
        logger.info("Number of rows fetched by object store:-" + objectStoreFoundCounter);
    }

    private void reEncryptData(List<DemographicEntity> applicantDemographic) throws Exception {
        int count = 0;
        for (DemographicEntity demographicEntity : applicantDemographic) {
            logger.info("pre registration id: " + demographicEntity.getPreRegistrationId());
            logger.info("encrypted : " + new String(demographicEntity.getApplicantDetailJson()));
            if (demographicEntity.getApplicantDetailJson() != null) {
                byte[] decryptedBytes = decrypt(demographicEntity.getApplicantDetailJson(), LocalDateTime.now(), decryptBaseUrl);
                if(decryptedBytes == null)
                    continue;
                count++;
                logger.info("decrypted: " + new String(decryptedBytes));
                byte[] ReEncrypted = encrypt(decryptedBytes, LocalDateTime.now(), encryptBaseUrl);
                logger.info("ReEncrypted: " + new String(ReEncrypted));
                if(isNewDatabase) {
                    logger.info("I am in new database");
                    demographicEntity.setApplicantDetailJson(ReEncrypted);
                    demographicEntity.setEncryptedDateTime(LocalDateTime.now());
                    demographicEntity.setDemogDetailHash(hashUtill(ReEncrypted));
                    demographicEntityList.add(demographicEntity);
                }
                else {
                    logger.info("i am in else false condition");
                    DemographicEntity demographicEntity1 = demographicRepository.findBypreRegistrationId(demographicEntity.getPreRegistrationId());
                    demographicEntity1.setApplicantDetailJson(ReEncrypted);
                    demographicEntity1.setEncryptedDateTime(LocalDateTime.now());
                    demographicEntity1.setDemogDetailHash(hashUtill(ReEncrypted));
                    demographicRepository.save(demographicEntity1);
                }
            }
        }
        logger.info("Total rows "+ applicantDemographic.size());
        logger.info("Total rows encrypted "+ count);
    }
}



