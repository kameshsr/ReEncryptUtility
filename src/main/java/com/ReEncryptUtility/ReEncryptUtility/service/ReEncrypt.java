package com.ReEncryptUtility.ReEncryptUtility.service;

import com.ReEncryptUtility.ReEncryptUtility.dto.CryptoManagerRequestDTO;
import com.ReEncryptUtility.ReEncryptUtility.dto.CryptoManagerResponseDTO;
import com.ReEncryptUtility.ReEncryptUtility.dto.RequestWrapper;
import com.ReEncryptUtility.ReEncryptUtility.dto.ResponseWrapper;
import com.ReEncryptUtility.ReEncryptUtility.entity.DemographicEntity;
import com.ReEncryptUtility.ReEncryptUtility.repository.ReEncrptyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.sql.SQLException;
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

//    public String reEncryptDatabaseValues(String query) throws SQLException {
//        logger.info("PostgreSQL JDBC Connection Testing ~");
//
//        try (Connection connection = getConnection()) {
//            Statement statement = connection.createStatement();
//            ResultSet rs = statement.executeQuery(query);
//            row = 0;
//            while (rs.next()&& row<3) {
//                logger.info("row=: " + row++);
//                logger.info("Pre_Reg_ID = " + rs.getString("prereg_id"));
//                byte[] demog_details = rs.getBytes("demog_detail");
//                logger.info("Encrypted demog_detail=\n" + new String(demog_details));
//                logger.info("demog_detail_hash\n" + rs.getString("demog_detail_hash"));
//                logger.info("account:-" + rs.getString("cr_by"));
//                if (demog_details.length > 0) {
//                    byte[] decrypted = decrypt(demog_details, LocalDateTime.now(), decryptBaseUrl);
//                    if (decrypted == null) {
//                        logger.info("Decrypted data is null");
//                        continue;
//                    }
//                    logger.info("decrypted pre-reg-data-:-\n" + new String(decrypted));
//                    byte[] ReEncrypted = encrypt(decrypted, LocalDateTime.now(), encryptBaseUrl);
//                    logger.info("ReEncrypted pre-reg-data-:-\n" + new String(ReEncrypted));
//
//                    try {
//                        String updateQuery =
//                                "update applicant_demographic set demog_detail=?, demog_detail_hash=?, encrypted_dtimes=?::timestamp where prereg_id=?";
//                        PreparedStatement stmt = targetDatabaseConnection.prepareStatement(updateQuery);
//                        stmt.setBytes(1, ReEncrypted);
//                        stmt.setString(2, hashUtill(ReEncrypted));
//                        LocalDateTime encryptionDateTime = DateUtils.getUTCCurrentDateTime();
//                        stmt.setTimestamp(3, Timestamp.valueOf(encryptionDateTime));
//                        stmt.setString(4, rs.getString("prereg_id"));
//                        int checkUpdateStatus = stmt.executeUpdate();
//                        if (checkUpdateStatus > 0) {
//                            logger.info("successfully updated");
//                        } else {
//                            logger.info("inserting new record");
//                            String insertQuery = "INSERT INTO applicant_demographic(prereg_id,demog_detail,demog_detail_hash," +
//                                    "encrypted_dtimes, status_code, lang_code, cr_appuser_id, cr_by, cr_dtimes, upd_by, upd_dtimes) "
//                                    + "VALUES(?,?,?,?::timestamp,?,?,?,?,?::timestamp,?,?::timestamp)";
//                            PreparedStatement stmt1 = targetDatabaseConnection.prepareStatement(insertQuery);
//                            stmt1.setString(1, rs.getString("prereg_id"));
//                            stmt1.setBytes(2, ReEncrypted);
//                            stmt1.setString(3, hashUtill(ReEncrypted));
//                            stmt1.setString(4, String.valueOf(Timestamp.valueOf(encryptionDateTime)));
//                            stmt1.setString(5, rs.getString("status_code"));
//                            stmt1.setString(6, rs.getString("lang_code"));
//                            stmt1.setString(7, rs.getString("cr_appuser_id"));
//                            stmt1.setString(8, rs.getString("cr_by"));
//                            stmt1.setString(9, rs.getString("cr_dtimes"));
//                            stmt1.setString(10, rs.getString("upd_by"));
//                            stmt1.setString(11, rs.getString("upd_dtimes"));
//                            int checkInsertStatus = stmt1.executeUpdate();
//                            if (checkInsertStatus > 0) {
//                                logger.info("successfully inserted");
//
//                            }
//                        }
//                        successFullRow++;
//                    } catch (SQLException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//            logger.info("Total rows=: " + row);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return "Success";
//    }

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
            //logger.info("In decrypt method of CryptoUtil service cryptoResourceUrl: " + cryptoResourceUrl + "/decrypt");
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

//    public static String hashUtill(byte[] bytes) {
//        return HMACUtils.digestAsPlainText(HMACUtils.generateHash(bytes));
//    }

    @Autowired
    private ReEncrptyRepository reEncrptyRepository;

    public void start() throws Exception {

        List<DemographicEntity> applicantDemographic = reEncrptyRepository.findAll();
        reEncryptData(applicantDemographic);
    }

    private void reEncryptData(List<DemographicEntity> applicantDemographic) throws Exception {
        int count = 0;
        for (DemographicEntity demographicEntity : applicantDemographic) {
            if (count++ > 5)
                break;
            System.out.println("pre registration id: " + demographicEntity.getPreRegistrationId());
            System.out.println("encrypted : " + new String(demographicEntity.getApplicantDetailJson()));
            if (demographicEntity.getApplicantDetailJson() != null) {
                byte[] decryptedBytes = decrypt(demographicEntity.getApplicantDetailJson(), LocalDateTime.now(), decryptBaseUrl);
                if(decryptedBytes == null)
                    continue;
                System.out.println("decrypted: " + new String(decryptedBytes));
                byte[] ReEncrypted = encrypt(decryptedBytes, LocalDateTime.now(), encryptBaseUrl);
                System.out.println("ReEncrypted: " + new String(ReEncrypted));
            }
        }
    }
}



