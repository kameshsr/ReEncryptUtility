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

/**
 * The Class ReEncrypt.
 * service class for re-encrypting the documents
 * @author Kamesh Shekhar Prasad
 * @since 1.0.0
 */

@Component
public class ReEncrypt {

    Logger logger = org.slf4j.LoggerFactory.getLogger(ReEncrypt.class);

    /**
     * restTemplate.
     * The restTemplate to make http calls
     */
    @Autowired
    RestTemplate restTemplate;

    /**
     * cryptoResourceUrl.
     * The cryptoResourceUrl is the url of the key manager service which is used for decrypting the documents.
     */
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
    private DemographicRepository demographicRepository;

    @Autowired
    private DocumentRepository documentRepository;

    /*
     * The objectStoreAdapter is used to get the documents from the object store
     */
    @Qualifier("S3Adapter")
    @Autowired
    private ObjectStoreAdapter objectStore;

    /**
     * isNewDatabase variable is used to check if the database is new or not.
     */
    @Value("${isNewDatabase:true}")
    private boolean isNewDatabase;

    @Value("${mosip.kernel.objectstore.account-name}")
    private String objectStoreAccountName;
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

    public List<DemographicEntity> demographicEntityList = new ArrayList<>();
    public List<DocumentEntity> documentEntityLists = new ArrayList<>();

    /**
     *
     * @param mapper
     */
    public ReEncrypt(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * This method is used to generate token for the key manager service.
     * @param url
     */
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

    /**
     * This method is used to decrypt the demographic data.
     * @param originalInput
     * @param localDateTime
     * @param decryptBaseUrl
     * @return
     * @throws Exception
     */
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
            decodedBytes = response.getBody().getResponse().getData().getBytes();
        } catch (Exception ex) {
            logger.error("Error in decrypt method of CryptoUtil service " + ex.getMessage());
        }
        return decodedBytes;
    }

    /**
     * This method is used to encrypt the demographic data.
     * @param originalInput
     * @param localDateTime
     * @param encryptBaseUrl
     * @return
     * @throws Exception
     */
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

    /**
     * This method is used to generate hash value.
     * @param bytes
     * @return
     */
    public static String hashUtill(byte[] bytes) {
        return HMACUtils.digestAsPlainText(HMACUtils.generateHash(bytes));
    }

    /**
     * This method is used as starting point for utility class.
     */
    public void start() throws Exception {
        DatabaseThreadContext.setCurrentDatabase(Database.PRIMARY);
        logger.info("sessionId", "idType", "id", "In start method of CryptoUtil service ");

        List<DemographicEntity> applicantDemographic = demographicRepository.findAll();
        reEncryptData(applicantDemographic);
        List<DocumentEntity> documentEntityList = documentRepository.findAll();
        reEncryptOldDocument(documentEntityList);
        logger.info("size of list" + documentEntityLists.size());
        if (isNewDatabase) {
            insertDataInNewDatabase();
        }
    }

    /**
     * This method creates connection to destination object store.
     * @param bucketName
     * @return
     */
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
                logger.error("Maximum retry limit exceeded. Could not obtain connection for " + bucketName + ". Retry count :" + retry, ExceptionUtils.getStackTrace(e));
                throw new ObjectStoreAdapterException(OBJECT_STORE_NOT_ACCESSIBLE.getErrorCode(), OBJECT_STORE_NOT_ACCESSIBLE.getErrorMessage(), e);
            } else {
                connection = null;
                retry = retry + 1;
                logger.error("Exception occured while obtaining connection for " + bucketName + ". Will try again. Retry count : " + retry, ExceptionUtils.getStackTrace(e));
                getConnection(bucketName);
            }
        }
        return connection;
    }


    /**
     * This method will insert data in new database.
     */
    private void insertDataInNewDatabase() {
        logger.info("sessionId", "idType", "id", "In insertDataInNewDatabase method of CryptoUtil service ");

        /**
         * Change the database to new database.
         */
        DatabaseThreadContext.setCurrentDatabase(Database.SECONDARY);
        logger.info("size of list" + demographicEntityList.size());
        logger.info("size of qa=upgrade" + demographicRepository.findAll().size());

        for (DemographicEntity demographicEntity : demographicEntityList) {
            if (demographicRepository.findBypreRegistrationId(demographicEntity.getPreRegistrationId()) == null) {
                DemographicEntity newDemographicEntity = new DemographicEntity();
                newDemographicEntity.setPreRegistrationId(demographicEntity.getPreRegistrationId());
                newDemographicEntity.setDemogDetailHash(demographicEntity.getDemogDetailHash());
                newDemographicEntity.setEncryptedDateTime(demographicEntity.getEncryptedDateTime());
                newDemographicEntity.setApplicantDetailJson(demographicEntity.getApplicantDetailJson());
                newDemographicEntity.setStatusCode(demographicEntity.getStatusCode());
                newDemographicEntity.setLangCode(demographicEntity.getLangCode());
                newDemographicEntity.setCrAppuserId(demographicEntity.getCrAppuserId());
                newDemographicEntity.setCreatedBy(demographicEntity.getCreatedBy());
                newDemographicEntity.setCreateDateTime(demographicEntity.getCreateDateTime());
                newDemographicEntity.setUpdatedBy(demographicEntity.getUpdatedBy());
                newDemographicEntity.setUpdateDateTime(demographicEntity.getUpdateDateTime());
                demographicRepository.save(newDemographicEntity);
            }
        }
        logger.info("size of list" + documentEntityLists.size());
        for (DocumentEntity documentEntities : documentEntityLists) {
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

    /**
     * This method will read data from object store re-encrypt and insert in same object store if isNewDatabase is false.
     * @param documentEntityList
     */
    private void reEncryptOldDocument(List<DocumentEntity> documentEntityList) {
        logger.info("Total rows in Document Entity:-" + documentEntityList.size());
        int objectStoreFoundCounter = 0;
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
                        InputStream sourceFile = objectStore.getObject(objectStoreAccountName,
                                documentEntity.getDemographicEntity().getPreRegistrationId(), null, null, key);
                        if (sourceFile != null) {
                            objectStoreFoundCounter++;
                            logger.info("sourceFile not null");
                            byte[] bytes = IOUtils.toByteArray(sourceFile);
                            byte[] decryptedBytes = decrypt(bytes, LocalDateTime.now(), decryptBaseUrl);
                            if (decryptedBytes == null) {
                                logger.info("decryptedBytes is null");
                                continue;
                            }
                            byte[] reEncryptedBytes = encrypt(decryptedBytes, LocalDateTime.now(), encryptBaseUrl);
                            String folderName = documentEntity.getDemographicEntity().getPreRegistrationId();
                            if (isNewDatabase) {
                                AmazonS3 connection = getConnection(folderName);
                                if (!connection.doesBucketExistV2(folderName))
                                    connection.createBucket(folderName);
                                connection.putObject(folderName, key, new ByteArrayInputStream(reEncryptedBytes), null);
                                documentEntity.setDocHash(hashUtill(reEncryptedBytes));
                                documentEntity.setEncryptedDateTime(LocalDateTime.now());
                                documentEntityLists.add(documentEntity);
                            } else {
                                objectStore.putObject(objectStoreAccountName, documentEntity.getDemographicEntity().getPreRegistrationId(), null, null, key, new ByteArrayInputStream(reEncryptedBytes));
                            }
                            List<DocumentEntity> currentDocumentEntityList = documentRepository.findByDemographicEntityPreRegistrationId(documentEntity.getDemographicEntity().getPreRegistrationId());
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
                }
            }
        }
        logger.info("Number of rows fetched by object store:-" + objectStoreFoundCounter);
    }

    /**
     * This method will read the data from database re-encrypt and store in database.
     * @param applicantDemographic
     * @throws Exception
     */
    private void reEncryptData(List<DemographicEntity> applicantDemographic) throws Exception {
        int count = 0;
        for (DemographicEntity demographicEntity : applicantDemographic) {
            logger.info("pre registration id: " + demographicEntity.getPreRegistrationId());
            if (demographicEntity.getApplicantDetailJson() != null) {
                byte[] decryptedBytes = decrypt(demographicEntity.getApplicantDetailJson(), LocalDateTime.now(), decryptBaseUrl);
                if (decryptedBytes == null)
                    continue;
                count++;
                byte[] reEncrypted = encrypt(decryptedBytes, LocalDateTime.now(), encryptBaseUrl);
                if (isNewDatabase) {
                    logger.info("I am in new database");
                    demographicEntity.setApplicantDetailJson(reEncrypted);
                    demographicEntity.setEncryptedDateTime(LocalDateTime.now());
                    demographicEntity.setDemogDetailHash(hashUtill(reEncrypted));
                    demographicEntityList.add(demographicEntity);
                } else {
                    logger.info("i am in else false condition");
                    DemographicEntity demographicEntity1 = demographicRepository.findBypreRegistrationId(demographicEntity.getPreRegistrationId());
                    demographicEntity1.setApplicantDetailJson(reEncrypted);
                    demographicEntity1.setEncryptedDateTime(LocalDateTime.now());
                    demographicEntity1.setDemogDetailHash(hashUtill(reEncrypted));
                    demographicRepository.save(demographicEntity1);
                }
            }
        }
        logger.info("Total rows " + applicantDemographic.size());
        logger.info("Total rows encrypted " + count);
    }
}



