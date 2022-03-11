package com.ReEncryptUtility.ReEncryptUtility.service;

import com.ReEncryptUtility.ReEncryptUtility.dto.CryptoManagerRequestDTO;
import com.ReEncryptUtility.ReEncryptUtility.dto.CryptoManagerResponseDTO;
import com.ReEncryptUtility.ReEncryptUtility.dto.RequestWrapper;
import com.ReEncryptUtility.ReEncryptUtility.dto.ResponseWrapper;
import com.ReEncryptUtility.ReEncryptUtility.entity.DemographicEntity;
import com.ReEncryptUtility.ReEncryptUtility.repository.ReEncrptyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.kernel.core.util.HMACUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
    private ReEncrptyRepository reEncryptRepository;

    String token = "";

    public int row;

    public int successFullRow;

    public ReEncrypt(ObjectMapper mapper, ReEncrptyRepository reEncryptRepository) {
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
    private ReEncrptyRepository reEncrptyRepository;

    public void start() throws Exception {

        List<DemographicEntity> applicantDemographic = reEncrptyRepository.findAll();
        reEncryptData(applicantDemographic);
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
                DemographicEntity demographicEntity1 = reEncrptyRepository.findBypreRegistrationId(demographicEntity.getPreRegistrationId());
                demographicEntity1.setApplicantDetailJson(ReEncrypted);
                demographicEntity1.setEncryptedDateTime(LocalDateTime.now());
                demographicEntity1.setDemogDetailHash(hashUtill(ReEncrypted));
                reEncrptyRepository.save(demographicEntity1);
            }
        }
        logger.info("Total rows "+ applicantDemographic.size());
        logger.info("Total rows encrypted "+ count);
    }
}



