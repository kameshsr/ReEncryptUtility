package com.ReEncryptUtility.ReEncryptUtility.service;

import com.ReEncryptUtility.ReEncryptUtility.dto.CryptoManagerRequestDTO;
import com.ReEncryptUtility.ReEncryptUtility.dto.CryptoManagerResponseDTO;
import com.ReEncryptUtility.ReEncryptUtility.dto.RequestWrapper;
import com.ReEncryptUtility.ReEncryptUtility.dto.ResponseWrapper;
import com.ReEncryptUtility.ReEncryptUtility.entity.DemographicEntity;
import com.ReEncryptUtility.ReEncryptUtility.entity.DocumentEntity;
import com.ReEncryptUtility.ReEncryptUtility.repository.DemographicRepository;
import com.ReEncryptUtility.ReEncryptUtility.repository.DocumentRepository;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.commons.khazana.spi.ObjectStoreAdapter;
import io.mosip.kernel.core.fsadapter.exception.FSAdapterException;
import io.mosip.kernel.core.util.HMACUtils;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;


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


    @Qualifier("S3Adapter")
    @Autowired
    private ObjectStoreAdapter objectStore;

    @Value("${mosip.kernel.objectstore.account-name}")
    private String objectStoreAccountName;

    @Value("${isNewDatabase")
    private String isNewDatabase;

    String token = "";

    public int row;

    public int successFullRow;

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

    @Autowired
    private DemographicRepository demographicRepository;

    @Autowired
    private DocumentRepository documentRepository;

    public void start() throws Exception {
        DatabaseThreadContext.setCurrentDatabase(Database.PRIMARY);

        List<DemographicEntity> applicantDemographic = demographicRepository.findAll();
        reEncryptData(applicantDemographic);
//        List<DocumentEntity> documentEntityList = documentRepository.findAll();
//        reEncryptDocument(documentEntityList);

    }

    private void reEncryptDocument(List<DocumentEntity> documentEntityList)  {
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
                        if (objectStore.exists("objectStoreAccountName", documentEntity.getDemographicEntity().getPreRegistrationId(), null, null, key) == false) {
                            logger.info("key not found in objectstore");
                            continue;
                        }
                        InputStream sourcefile = objectStore.getObject("objectStoreAccountName",
                                documentEntity.getDemographicEntity().getPreRegistrationId(), null, null, key);
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

                            objectStore.putObject("objectStoreAccountName", documentEntity.getDemographicEntity().getPreRegistrationId(), null, null, key, new ByteArrayInputStream(reEncryptedBytes));

                            List<DocumentEntity> currentDocumentEntityList =  documentRepository.findByDemographicEntityPreRegistrationId(documentEntity.getDemographicEntity().getPreRegistrationId());
                            for (DocumentEntity currentDocumentEntity : currentDocumentEntityList) {
                                currentDocumentEntity.setDocHash(hashUtill(reEncryptedBytes));
                                currentDocumentEntity.setEncryptedDateTime(LocalDateTime.now());
                                demographicRepository.save(currentDocumentEntity.getDemographicEntity());
                                documentRepository.save(currentDocumentEntity);
                            }
                        }

                    } catch (AmazonS3Exception | FSAdapterException | IOException e) {
                        //e.printStackTrace();
                        logger.info("Exception:- bucket not found");

                        throw new AmazonS3Exception("bucket not found");
                    } catch (Exception e) {
                        e.printStackTrace();
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
            if (count >5)
                break;
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


                if(isNewDatabase=="true"){
                    DemographicEntity demographicEntity1 = demographicRepository.findBypreRegistrationId(demographicEntity.getPreRegistrationId());
                    demographicEntity1.setApplicantDetailJson(ReEncrypted);
                    demographicEntity1.setEncryptedDateTime(LocalDateTime.now());
                    demographicEntity1.setDemogDetailHash(hashUtill(ReEncrypted));
                    DatabaseThreadContext.setCurrentDatabase(Database.SECONDARY);
                    demographicRepository.save(demographicEntity1);
                    DatabaseThreadContext.setCurrentDatabase(Database.PRIMARY);
                }
                else {
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



