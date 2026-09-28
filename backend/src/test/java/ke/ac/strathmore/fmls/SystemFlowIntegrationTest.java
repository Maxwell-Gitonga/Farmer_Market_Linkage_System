package ke.ac.strathmore.fmls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Admin;
import ke.ac.strathmore.fmls.domain.MarketPrice;
import ke.ac.strathmore.fmls.repository.AdminRepository;
import ke.ac.strathmore.fmls.repository.MarketPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Functional + integration tests (Proposal §3.6) for the main use cases, run against an in-memory database.
 * The ML module is deliberately unreachable here, so the backend's graceful fallback paths are exercised;
 * the ML models themselves are tested in ml-service/tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SystemFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AdminRepository admins;
    @Autowired MarketPriceRepository prices;
    @Autowired BCryptPasswordEncoder encoder;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Admin a = new Admin();
        a.setName("Test Admin");
        a.setEmail("admin@test.ke");
        a.setStatus(AccountStatus.ACTIVE);
        a.setPasswordHash(encoder.encode("Admin@123"));
        admins.save(a);
        // 20 recent market-day prices for Maize in Nakuru
        LocalDate d = LocalDate.now();
        int n = 0;
        while (n < 20) {
            if (d.getDayOfWeek() == DayOfWeek.TUESDAY || d.getDayOfWeek() == DayOfWeek.FRIDAY) {
                prices.save(new MarketPrice("Maize", "Nakuru", 50.0 + (n % 3), d, "TEST"));
                n++;
            }
            d = d.minusDays(1);
        }
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String token, Object body, int expectedStatus) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult r = mvc.perform(req).andReturn();
        String content = r.getResponse().getContentAsString();
        assertThat(r.getResponse().getStatus()).as(content).isEqualTo(expectedStatus);
        return content.isBlank() ? json.createObjectNode() : json.readTree(content);
    }

    private String register(String role, String name, String phone, String location, String prefs) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("role", role);
        body.put("name", name);
        body.put("phone", phone);
        body.put("password", "Secret@123");
        body.put("location", location);
        body.put("preferences", prefs);
        return call(post("/api/auth/register"), null, body, 200).path("token").asText();
    }

    private String login(String phone, String password, int status) throws Exception {
        return call(post("/api/auth/login"), null, java.util.Map.of("phone", phone, "password", password), status)
                .path("token").asText();
    }

    @Test
    void endToEndMarketLinkageFlow() throws Exception {
        // Registration + single sign-in with automatic role identification
        String farmer = register("FARMER", "Jane Farmer", "0712345678", "Nakuru", null);
        JsonNode me = call(get("/api/auth/me"), farmer, null, 200);
        assertThat(me.path("role").asText()).isEqualTo("FARMER");
        assertThat(me.path("status").asText()).isEqualTo("PENDING");
        String buyer = register("BUYER", "Soko Buyer", "+254 722 111 222", "Nairobi", "Maize, Beans");
        assertThat(call(get("/api/auth/me"), buyer, null, 200).path("phone").asText()).isEqualTo("0722111222");
        String admin = login("admin@test.ke", "Admin@123", 200);

        // Farmer lists produce → productID + price recommendation (fallback estimate, ML offline)
        var listing = java.util.Map.of("cropType", "maize", "quantity", 1000, "harvestDate",
                LocalDate.now().plusDays(3).toString(), "location", "Nakuru");
        JsonNode created = call(post("/api/products"), farmer, listing, 200);
        long productId = created.path("product").path("id").asLong();
        assertThat(created.path("product").path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("product").path("cropType").asText()).isEqualTo("Maize");
        JsonNode rec = created.path("recommendation");
        assertThat(rec.path("sufficientData").asBoolean()).isTrue();
        assertThat(rec.path("recommendedPrice").asDouble()).isBetween(45.0, 60.0);
        assertThat(rec.path("fallback").asBoolean()).isTrue();

        // Pending farmers cannot publish until approved by the administrator
        call(post("/api/products/" + productId + "/publish"), farmer,
                java.util.Map.of("price", 52, "acceptedRecommendation", true), 403);
        long farmerId = me.path("id").asLong();
        call(post("/api/admin/farmers/" + farmerId + "/approve"), admin, null, 200);
        JsonNode published = call(post("/api/products/" + productId + "/publish"), farmer,
                java.util.Map.of("price", 52, "acceptedRecommendation", false), 200);
        assertThat(published.path("product").path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(published.path("matchedBuyers").size()).isGreaterThanOrEqualTo(1);

        // Role-based access control
        call(post("/api/products"), buyer, listing, 403);
        call(get("/api/admin/users"), farmer, null, 403);
        call(get("/api/products"), null, null, 401);

        // Buyer searches and purchases (sequence diagram 2)
        JsonNode results = call(get("/api/products?crop=mai"), buyer, null, 200);
        assertThat(results.size()).isEqualTo(1);
        assertThat(call(get("/api/buyer/recommendations"), buyer, null, 200).size()).isEqualTo(1);
        call(post("/api/transactions"), buyer, java.util.Map.of("productId", productId, "quantity", 5000), 400);
        JsonNode purchase = call(post("/api/transactions"), buyer, java.util.Map.of("productId", productId, "quantity", 1000), 200);
        assertThat(purchase.path("message").asText()).isEqualTo("Purchase confirmed");
        assertThat(call(get("/api/products/" + productId), buyer, null, 200).path("product").path("status").asText())
                .isEqualTo("SOLD");

        // Farmer completes the sale → recorded as market data
        long txId = purchase.path("transactionId").asLong();
        long before = prices.count();
        call(patch("/api/transactions/" + txId + "/status"), farmer, java.util.Map.of("status", "COMPLETED"), 200);
        assertThat(prices.count()).isEqualTo(before + 1);

        // Messaging
        call(post("/api/messages"), buyer, java.util.Map.of("counterpartId", farmerId, "content", "Thanks!"), 200);
        assertThat(call(get("/api/messages/threads"), farmer, null, 200).path(0).path("unread").asLong()).isEqualTo(1);

        // Admin monitoring and reports
        assertThat(call(get("/api/admin/logs"), admin, null, 200).size()).isGreaterThan(5);
        assertThat(call(get("/api/admin/reports?days=30"), admin, null, 200).path("byCrop").path(0).path("cropType").asText())
                .isEqualTo("Maize");

        // Suspension takes effect immediately
        long buyerId = call(get("/api/auth/me"), buyer, null, 200).path("id").asLong();
        call(patch("/api/admin/users/BUYER/" + buyerId + "/status"), admin, java.util.Map.of("status", "SUSPENDED"), 200);
        call(get("/api/products"), buyer, null, 403);
        login("0722111222", "Secret@123", 403);
    }

    @Test
    void insufficientHistoricalDataReturnsLowConfidenceAlert() throws Exception {
        String farmer = register("FARMER", "New Crop Farmer", "0799000111", "Kiambu", null);
        var listing = java.util.Map.of("cropType", "Macadamia", "quantity", 50, "harvestDate",
                LocalDate.now().toString(), "location", "Kiambu");
        JsonNode rec = call(post("/api/products"), farmer, listing, 200).path("recommendation");
        assertThat(rec.path("sufficientData").asBoolean()).isFalse();
        assertThat(rec.path("confidenceLabel").asText()).isEqualTo("low");
        assertThat(rec.path("message").asText()).contains("Limited data");
    }

    @Test
    void validationAndDuplicateRegistration() throws Exception {
        register("BUYER", "Buyer One", "0733000111", "Kisumu", "Tomatoes");
        var dup = new java.util.HashMap<String, Object>(java.util.Map.of("role", "BUYER", "name", "Again",
                "phone", "0733000111", "password", "Secret@123", "location", "Kisumu"));
        call(post("/api/auth/register"), null, dup, 409);
        dup.put("phone", "12345");
        call(post("/api/auth/register"), null, dup, 400);
        login("0733000111", "wrong-password", 401);
    }
}
