import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import pngbase64.ImageUtils;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.rabbitmq.tools.json.*;
import org.json.simple.JSONObject;

public class Publisher {

  private static final String EXCHANGE_NAME = "topic_logs";

  public static void main(String[] argv) {
    Connection connection = null;
    Channel channel = null;
    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");
      factory.setUsername("username");
      factory.setPassword("mypass");
      
      connection = factory.newConnection();
      channel = connection.createChannel();

      channel.exchangeDeclare(EXCHANGE_NAME, "topic");

      String routingKey = getRouting(argv);
      String imgFilePath = getMessage(argv);
      // Encode the PNG into Base64 String
      String imgStr = encodeToBase64(imgFilePath);
      // Create the warp sent to topic exchange
      String warp = createWarp(imgStr);

      channel.basicPublish(EXCHANGE_NAME, routingKey, null, warp.getBytes("UTF-8"));
      System.out.println(" [x] Sent '" + routingKey + "':'" + warp + "'");
    }
    catch  (Exception e) {
      e.printStackTrace();
    }
    finally {
      if (connection != null) {
        try {
          connection.close();
        }
        catch (Exception ignore) {}
      }
    }
  }

  private static String encodeToBase64(String imgPath) {
      BufferedImage img = null;
      String imgstr = null;
      try {
          img = ImageIO.read(new File(imgPath));
          BufferedImage newImg;
          ImageUtils encoder = new ImageUtils();
          imgstr = encoder.encodeToString(img, "png");
          System.out.println(imgstr);
      } catch (Exception e) {
          e.printStackTrace();
      }

      return imgstr;
  }

  private static String createWarp(String imgStr) {
      JSONObject obj = new JSONObject();
      try {
          String uid = java.util.UUID.randomUUID().toString();
          obj.put("warp_id", uid);
          obj.put("data_type", "image_plate");
          obj.put("data", imgStr);
      } catch (Exception e) {
          e.printStackTrace();
      }

      return obj.toJSONString();
  }

  private static String getRouting(String[] strings) {
    if (strings.length < 1)
    	    return "anonymous.info";
    return strings[0];
  }

  private static String getMessage(String[] strings) {
    if (strings.length < 2)
    	    return "Hello World!";
    return joinStrings(strings, " ", 1);
  }

  private static String joinStrings(String[] strings, String delimiter, int startIndex) {
    int length = strings.length;
    if (length == 0 ) return "";
    if (length < startIndex ) return "";
    StringBuilder words = new StringBuilder(strings[startIndex]);
    for (int i = startIndex + 1; i < length; i++) {
        words.append(delimiter).append(strings[i]);
    }
    return words.toString();
  }
}
