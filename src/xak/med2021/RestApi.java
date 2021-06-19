package xak.med2021;

import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.xsite.server.Context;
import org.xsite.webapp.RestModule;

public class RestApi extends RestModule {

    public static final String AUTH_TOKEN = Base64.getEncoder().encodeToString("testuser:password123".getBytes());

    public static final String BASE_PATH = "/";

    private RestClient rsClient;

    private ExecutorService executor = Executors.newCachedThreadPool();

    public RestApi() {
        super(BASE_PATH);
//        cors("*");

        rsClient = Main.rsClient;
    }

    @GET("/api/v1/phones/{id}")
    public void getPhones() {

//        json(200, Map.of(
//            "phones", List.of("+7 231 123 43 33", "+7 988 7768 767 8")
//        ));

    }

    @GET("/user/{id}")
    public void getUser() {
        System.out.println("Check Auth...");
        auth();
        System.out.println("Auth OK");

        try {
            int id = param("id").getInt();
            System.out.println("ID: " + id);

            String userName = executor.submit(() -> "").get();
            String phone = executor.submit(() -> rsClient.getPhone(id)).get();

            System.out.println("UserName: " + userName);
            System.out.println("Phone: " + phone);

            if( userName == null ) {
                json(200, new UserResponse(2, null, null));
                return;
            }
            if( userName.equals("##TIMEOUT##") ) {
                json(200, new UserResponse(1, null, null));
                return;
//                json(500, "{\"msg\":\"WS endpoint not available\"}");
            }

            json(200, new UserResponse(0, userName, phone));
        } catch( Exception e ) {
            e.printStackTrace();
            json(200, new UserResponse(2, null, null));
//            json(500, "{\"msg\":\"" + e.getMessage() + "\"}");
        }
    }

    private void auth() {
        Context ctx = Context.current();

        String auth = ctx.header("Authorization");
        if( auth != null ) {
            String[] split = auth.trim().split("\\s", 2);
            if( split.length == 2 && "Basic".equals(split[0]) ) {
                String token = split[1];
                if( AUTH_TOKEN.equals(token) )
                    return;
            }
        }

        AjaxResponse res = new AjaxResponse(1, "Not authorized");
        ctx.header("WWW-Authenticate", "Basic realm=\"Auth\"");
        json(401, res);
        throw res;
    }

    private static class UserResponse {
        int code;
        String name;
        String phone;

        public UserResponse(int code, String name, String phone) {
            this.code = code;
            this.name = name;
            this.phone = phone;
        }

        public UserResponse() {
        }

        public String toJson() {
            return "{\"code\":" + code + ",\"name\":\"" + name + "\",\"phone\":\"" + phone + "\"}";
        }
    }

}
