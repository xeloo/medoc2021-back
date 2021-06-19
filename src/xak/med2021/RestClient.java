package xak.med2021;

import java.util.List;

import ru.xeloo.http.parser.HttpRequest;
import ru.xeloo.json.JSON;

public class RestClient {

    private static final String ENDPOINT = "http://localhost:9080";
    private final HttpRequest http;

    public RestClient() {
        String endpoint = System.getenv("rs.endpoint");
        System.out.println("RS ENDPOINT: " + endpoint);
        http = new HttpRequest()
            .baseUrl(endpoint == null ? ENDPOINT : endpoint)
            .timeout(5000);
    }

    public String getPhone(int id) {
        try {
            http.setHeader("Accept", "application/json");
            String res = http.get("/api/v1/phones/" + id).string();
            List<String> phones = JSON.parse(PhonesResponse.class, res).phones;

            System.out.println("RS PHONES: " + JSON.stringify(phones));

            if( phones == null || phones.isEmpty() )
                return null;
            return phones.get(0);
        } catch( Exception e ) {
            e.printStackTrace();
            return null;
        }
    }


    private static class PhonesResponse {
        List<String> phones;
    }
}
