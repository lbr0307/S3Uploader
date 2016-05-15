# Transcriptic RabbitMQ Image Rotation Challenge

This is the implementation on the requirements from Transcriptic RabbitMQ Image Rotation Challenge.

## Basic Structure of the Project

* The whole project consists of 4 modules:

	* Publisher Module (`Publisher.java`):
		* Reads in a PNG image A
		* Encodes A into Base64 string S
		* Sends S in a warp (format required in writeup) to the topic exchange T using rounting key from command line

	* S3 Uploader Module (`S3Upload.java`):
		* Listens to the topic exchange T using binding key "UploadS3"
		* When receives a warp, it:
			* Decodes the image data from Base64 to png file B
			* Rotates B by 180 degrees
			* Sends B to S3 bucket "transcriptic-interview"
			* Gets the uploading result from S3, the results can be of 3 distinct types
				* If result is sucess: it sends a callback message (warp required in writeup) to T using rounting key "SucessS3"
				* If result is client error: it saves the image to local directory, since it is not related to network
                * If result is server error: it retries uploading in a exponential backoff manner, if we still get error, save it to local directory

    * Callback Receiver Module (`CallbackReceiver.java`):
    	* Listens to the topic exchange T using binding key "UploadS3"
    	* Print out the warp when receives

    * Image Utility Module (`pngbase64/ImageUtils.java`):
    	* Encodes png file into Base64 string
    	* Decode Base64 string into png file
    	* Rotate png file by any degrees

## Running the Project

The basic steps for running this project are:

1. Start the RabbitMQ server, cd to its folder:

	```
	$ sbin/rabbitmq-server
	```

2. Cd to project folder

	2.1. Open a terminal and start S3 Uploader:

	```
	$ ant
	```

	2.2. Open a terminal and compile Publisher and Callback Receiver:

	```
	$ javac -cp .:lib/rabbitmq-client.jar:lib/json-simple-1.1.jar Publisher.java CallbackReceiver.java
	$ export CP=.:lib/commons-io-1.2.jar:lib/commons-cli-1.1.jar:lib/rabbitmq-client.jar:lib/json-simple-1.1.jar 

	2.3 Start Callback Receiver:

	```
	$ java -cp $CP CallbackReceiver
	```


	2.4 Start Publisher and send png image "result.png" using rounting key "UploadS3":

	```
	$ java -cp $CP Publisher "UploadS3" "result.png"
	```

**NOTE:** The project also includes an Ant build.xml file to run the sample.
