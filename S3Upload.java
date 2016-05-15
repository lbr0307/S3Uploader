import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;  
import java.util.concurrent.Executors;  

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;

import com.rabbitmq.client.*;
import pngbase64.ImageUtils;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.apache.commons.io.FileUtils;

public class S3Upload {

    private static final String EXCHANGE_NAME = "topic_logs";
    private static final String BINDING_KEY = "UploadS3";
    private static final String ROUNTING_KEY_CALLBACK = "SucessS3";
    private static final String WARP_TYPE = "image_plate";
    private static int THREAD_POOL_SIZE = 5;
    private static final int MAX_WAIT_INTERVAL = 3000;
    private static final int MAX_RETRIES = 5;

    private static Channel channel = null;
    private static int pngNum = 0;

    private static enum UploadResults {
        SUCCESS, 
        CLIENT_ERROR, 
        SERVER_ERROR,
        UNKOWN_ERROR
    }

    // The thread for uploader
    public static class WorkerThread implements Runnable {

        private String id;
        private File imgFile;
        private String bucketName;
        private String key;
        private boolean isRetry;
        
        public WorkerThread(String id, File imgFile, String bucketName, String key, boolean isRetry){
            this.id = id;
            this.imgFile = imgFile;  
            this.bucketName = bucketName; 
            this.key = key; 
            this.isRetry = isRetry; 
        }  
        
        public void run() {  
            System.out.println(Thread.currentThread().getName() + " (Start) Uploading: " + key);  
            UploadResults result = processmessage();

            // The logic on dealing with the UploadResults is:
            //     - SUCCESS: send a callback warp using rounting key ROUNTING_KEY_CALLBACK;
            //     - CLIENT_ERROR: save the image to local directory, since it is not related to network;
            //     - SERVER_ERROR: retry uploading in a exponential backoff manner, if we still get
            //                     the same error, save it to local directory.
            if (result == UploadResults.SUCCESS) {
                String warpForCallback = createWarpForCallback(id, WARP_TYPE, bucketName, key);
                try {
                    channel.basicPublish(EXCHANGE_NAME, ROUNTING_KEY_CALLBACK, null, warpForCallback.getBytes("UTF-8"));
                    System.out.println(" [x] Sent '" + ROUNTING_KEY_CALLBACK + "':'" + warpForCallback + "'");
                    FileUtils.forceDelete(imgFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (result == UploadResults.CLIENT_ERROR) {
                System.out.println("The warp of id: " + id + " failed to be sent to S3 and will be saved to local directory!");
                try {
                    FileUtils.copyFileToDirectory(imgFile, new File("Failed_Transferrd_Images"));
                    FileUtils.forceDelete(imgFile);                 
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    result = reuploadAndWaitForResult(bucketName, key, imgFile);

                    // If the result is still SERVER_ERROR or CLIENT_ERROR after retry, 
                    // save the failed files locally
                    if (result == UploadResults.SERVER_ERROR || result == UploadResults.CLIENT_ERROR) {
                        System.out.println("The warp of id: " + id + " failed to be sent to S3 and will be saved to local directory!");
                        try {
                            FileUtils.copyFileToDirectory(imgFile, new File("Failed_Transferrd_Images"));
                            FileUtils.forceDelete(imgFile);                   
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            System.out.println(Thread.currentThread().getName()+" (End) Uploading: " + key); 
        }  
        
        private UploadResults processmessage() {  
            UploadResults result = null;
            try {
                result = UploadToS3(imgFile, bucketName, key, isRetry);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return result;
        }  
    }  

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("username");
        factory.setPassword("mypass");

        Connection connection = factory.newConnection();
        channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, BINDING_KEY);

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        JSONParser parser = new JSONParser();

        // Create a thread pool of size THREAD_POOL_SIZE
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        Consumer consumer = new DefaultConsumer(channel) {
          @Override
          public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            String warp = new String(body, "UTF-8");
            JSONObject obj = null;
            try {
                obj = (JSONObject) parser.parse(warp);
                String id = (String) obj.get("warp_id");
                String type = (String) obj.get("data_type");

                // Make sure we got the right warp for png files
                if (type.equals(WARP_TYPE)) {
                    String imgStr = (String) obj.get("data");

                    // Decode Base64 block into png file
                    BufferedImage img = decodeToPNG(imgStr);

                    // Rotate received png file
                    BufferedImage imgRotated = rotateImageByRadian(img, Math.PI);
                    pngNum++;
                    File imgFile = new File(id + ".png" );
                    ImageIO.write(imgRotated, "png", imgFile);
                    System.out.println("WARP ID: " + String.valueOf(pngNum));

                    // Upload the rotated png file to S3
                    String bucketName = "transcriptic-interview";
                    String key = imgFile.getName();

                    // Put a worker thread into thread pool
                    WorkerThread worker = new WorkerThread(id, imgFile, bucketName, key, false);
                    executor.execute(worker);
                } else {
                    System.out.println("The warp of id: " + id + " is not a valid image!");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println(" [x] Received '" + envelope.getRoutingKey());
          }
        };
        channel.basicConsume(queueName, true, consumer);
    }

    private static String createWarpForCallback(String id, String type, String bucketName, String key) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("warp_id", id);
            obj.put("data_type", "image_plate");
            JSONObject objInfo = new JSONObject();
            objInfo.put("bucket", bucketName);
            objInfo.put("key", key);
            obj.put("s3_info", objInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return obj.toJSONString();
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

    public static UploadResults UploadToS3(File file, String bucketName, String key, boolean isRetry) throws IOException {
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (~/.aws/credentials), and is in valid format.", e);
        }

        AmazonS3 s3 = new AmazonS3Client(credentials);
        Region usWest2 = Region.getRegion(Regions.US_WEST_2);
        s3.setRegion(usWest2);

        System.out.println("===========================================");
        System.out.println("Getting Started with Amazon S3");
        System.out.println("===========================================\n");

        TransferManager tx = new TransferManager(credentials);
        Upload myUpload = null;
        UploadResults result = UploadResults.SUCCESS;

        try {
            PutObjectRequest request = new PutObjectRequest(bucketName, key, file);
            request.setGeneralProgressListener(new ProgressListener() {
                @Override
                public void progressChanged(ProgressEvent progressEvent) {
                    System.out.println("Transferred bytes: " + 
                            progressEvent.getBytesTransferred());
                }
            });

            System.out.println("Uploading a new object to S3 from a file");
            myUpload = tx.upload(request);

            try {
                myUpload.waitForCompletion();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to Amazon S3, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
            System.out.println("Failed to upload " + file.getName() + " to S3 due to server error.");
            result = UploadResults.SERVER_ERROR;
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with S3, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
            System.out.println("Failed to upload " + file.getName() + " to S3 due to server error.");
            result = UploadResults.CLIENT_ERROR;
        }

        return result;
    }

    /*
     * Performs an asynchronous reuploading operation, then polls for the result of the
     * operation using an incremental delay.
     */
    public static UploadResults reuploadAndWaitForResult(String bucketName, String key, File file) {
        UploadResults result = null;
        try {
            int retries = 0;
            boolean retry = false;
            do {
                long waitTime = Math.min(getWaitTimeExp(retries), MAX_WAIT_INTERVAL);
                System.out.println("Waiting Time Interval: " + waitTime + "\n");
                // Wait for the result.
                Thread.sleep(waitTime);

                // Get the result of the asynchronous operation.
                result = UploadToS3(file, bucketName, key, true);

                if (UploadResults.SUCCESS == result) {
                    retry = false;
                } else if (UploadResults.SERVER_ERROR == result) {
                    retry = true;
                } else if (UploadResults.CLIENT_ERROR == result) {
                    retry = true;
                } else {
                    retry = false;
                }
            } while (retry && (retries++ < MAX_RETRIES));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /*
     * Returns the next wait interval, in milliseconds, using an exponential
     * backoff algorithm.
     */
    public static long getWaitTimeExp(int retryCount) {
        long waitTime = ((long) Math.pow(2, retryCount) * 100L);
        return waitTime;
    }

}
