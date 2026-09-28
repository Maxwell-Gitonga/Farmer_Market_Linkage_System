package ke.ac.strathmore.fmls.config;

import ke.ac.strathmore.fmls.security.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** REST API configuration: CORS for the Angular app, access-control interceptor and HTTP clients. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AppProperties props;

    public WebConfig(AuthInterceptor authInterceptor, AppProperties props) {
        this.authInterceptor = authInterceptor;
        this.props = props;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.getCorsOrigins().toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition");
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Client for prediction / recommendation calls to the Python ML module. */
    @Bean(name = "mlRestTemplate")
    public RestTemplate mlRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(props.getMlTimeoutSeconds() * 1000);
        return new RestTemplate(f);
    }

    /** Client with a long timeout for model (re)training. */
    @Bean(name = "mlTrainingRestTemplate")
    public RestTemplate mlTrainingRestTemplate() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(props.getMlTrainTimeoutSeconds() * 1000);
        return new RestTemplate(f);
    }
}
