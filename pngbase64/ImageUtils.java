package pngbase64;

import java.io.IOException;
import sun.misc.BASE64Encoder;
import sun.misc.BASE64Decoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import java.applet.Applet;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.net.URL;

import java.applet.Applet;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.net.URL;

public class ImageUtils {

    /**
     * Decode string to image
     * @param imageString The string to decode
     * @return decoded image
     */
    public static BufferedImage decodeToImage(String imageString) {
        BufferedImage image = null;
        byte[] imageByte;
        try {
            BASE64Decoder decoder = new BASE64Decoder();
            imageByte = decoder.decodeBuffer(imageString);
            ByteArrayInputStream bis = new ByteArrayInputStream(imageByte);
            image = ImageIO.read(bis);
            bis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return image;
    }

    /**
     * Rotate string to image
     * @param imageString The string to rotate
     * @param radian The radian to rotate
     * @return rotated image
     */
    public static BufferedImage rotateImage(BufferedImage image, double radian) {
        BufferedImage img = null;
        try {
            AffineTransform transform = new AffineTransform();
            transform.rotate(radian, image.getWidth()/2, image.getHeight()/2);
            AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
            img = op.filter(image, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return img;
    }

    /**
     * Encode image to string
     * @param image The image to encode
     * @param type jpeg, bmp, ...
     * @return encoded string
     */
    public static String encodeToString(BufferedImage image, String type) {
        String imageString = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            ImageIO.write(image, type, bos);
            byte[] imageBytes = bos.toByteArray();

            BASE64Encoder encoder = new BASE64Encoder();
            imageString = encoder.encode(imageBytes);

            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageString;
    }

    public static void main (String args[]) throws IOException {
        BufferedImage img = ImageIO.read(new File("result_1_4.png"));
        BufferedImage newImg;
        String imgstr;
        imgstr = encodeToString(img, "png");
        System.out.println(imgstr);
        newImg = decodeToImage(imgstr);
        ImageIO.write(newImg, "png", new File("CopyOfTestImage.png"));
    }
}
