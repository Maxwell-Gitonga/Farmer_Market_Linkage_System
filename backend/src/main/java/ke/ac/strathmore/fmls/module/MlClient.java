package ke.ac.strathmore.fmls.module;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ke.ac.strathmore.fmls.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * REST client for the Machine Learning Module (Python). Implements the architecture arrows
 * "Send Data for Prediction" and "Prediction Results & Insights" (Figure 3.1).
 */
@Component
public class MlClient {

    private static final Logger log = LoggerFactory.getLogger(MlClient.class);

    private final RestTemplate rest;
    private final RestTemplate trainingRest;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public MlClient(@Qualifier("mlRestTemplate") RestTemplate rest,
                    @Qualifier("mlTrainingRestTemplate") RestTemplate trainingRest,
                    AppProperties props) {
        this.rest = rest;
        this.trainingRest = trainingRest;
        this.baseUrl = props.getMlServiceUrl().replaceAll("/+$", "");
    }

    public JsonNode post(String path, Object body) {
        return exchange(rest, path, body);
    }

    public JsonNode postLong(String path, Object body) {
        return exchange(trainingRest, path, body);
    }

    public JsonNode get(String path) {
        try {
            String json = rest.getForObject(baseUrl + path, String.class);
            return mapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            throw new MlUnavailableException("ML module unavailable: " + e.getMessage(), e);
        }
    }

    public boolean isUp() {
        try {
            return "UP".equals(get("/health").path("status").asText());
        } catch (MlUnavailableException e) {
            return false;
        }
    }

    private JsonNode exchange(RestTemplate client, String path, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(body), headers);
            String json = client.postForObject(baseUrl + path, entity, String.class);
            return mapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            log.warn("ML call {} failed: {}", path, e.getMessage());
            throw new MlUnavailableException("ML module unavailable: " + e.getMessage(), e);
        }
    }
}
