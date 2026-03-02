The haarcascade_frontalface_default.xml file is bundled inside the
org.openpnp:opencv JAR and is extracted to the data directory at
runtime by FaceRecognitionEngine.

To embed it directly in the application, copy it from:
  <opencv-jar>/haarcascades/haarcascade_frontalface_default.xml

into this directory before building. The extraction code in
FaceRecognitionEngine.extractCascade() will skip the extraction step
if the file already exists on disk (in the data directory).
