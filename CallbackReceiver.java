import com.rabbitmq.client.*;
import pngbase64.ImageUtils;
import java.io.IOException;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class CallbackReceiver {

  private static final String EXCHANGE_NAME = "topic_logs";

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    factory.setUsername("username");
    factory.setPassword("mypass");
    
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.exchangeDeclare(EXCHANGE_NAME, "topic");
    String queueName = channel.queueDeclare().getQueue();
    
    ArrayList<String> binding_keys = new ArrayList<String>();
    binding_keys.add("SucessS3");
    for (String bindingKey : binding_keys) {
      channel.queueBind(queueName, EXCHANGE_NAME, bindingKey);
    }

    System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

    Consumer consumer = new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        String warp = new String(body, "UTF-8");
        System.out.println(" [x] Received '" + envelope.getRoutingKey() + "':'" + warp + "'");
      }
    };
    channel.basicConsume(queueName, true, consumer);
  }

  public static BufferedImage decodeToPNG(String imageString) {
    BufferedImage image = null;
    try {
        ImageUtils decoder = new ImageUtils();
        image = decoder.decodeToImage(imageString);
    } catch (Exception e) {
          e.printStackTrace();
    }
    return image;
  }

  public static BufferedImage rotateImageByRadian(BufferedImage image, double radian) {
    BufferedImage img = null;
    try {
        ImageUtils rotater = new ImageUtils();
        img = rotater.rotateImage(image, radian);
    } catch (Exception e) {
          e.printStackTrace();
    }

    return img;
  }
}
