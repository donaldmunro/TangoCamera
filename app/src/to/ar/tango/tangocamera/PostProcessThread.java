/*
Copyright (c) 2017 Donald Munro

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package to.ar.tango.tangocamera;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.media.MediaActionSound;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import com.androidadvance.topsnackbar.TSnackbar;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static java.lang.Math.abs;

class PostProcessThread extends AsyncTask<Void, String, Boolean>
//======================================================================
{
   static final private String TAG = "PostProcessThread";

   final private StringBuilder messages = new StringBuilder(), errors = new StringBuilder();

   final MainActivity activity;
   int androidXAxis, androidYAxis;
   private float[] I = new float[16], IR = new float[16];

   public PostProcessThread(MainActivity activity) { this.activity = activity; }

   @Override
   protected Boolean doInBackground(Void... Vs)
   //---------------------------------------------------------------
   {
      MediaActionSound sound = new MediaActionSound();
      sound.play(MediaActionSound.SHUTTER_CLICK);
      switch (activity.deviceRotation)
      {
         case Surface.ROTATION_90:
            androidXAxis = SensorManager.AXIS_Y;androidYAxis = SensorManager.AXIS_MINUS_X; break;
         case Surface.ROTATION_180:
            androidXAxis = SensorManager.AXIS_MINUS_X; androidYAxis = SensorManager.AXIS_MINUS_Y; break;
         case Surface.ROTATION_270:
            androidXAxis = SensorManager.AXIS_MINUS_Y; androidYAxis = SensorManager.AXIS_X; break;
         case Surface.ROTATION_0:
         default:
            androidXAxis = SensorManager.AXIS_X; androidYAxis = SensorManager.AXIS_Y; break;
      }
      Matrix.setIdentityM(I, 0);
      SensorManager.remapCoordinateSystem(I, androidXAxis, androidYAxis, IR);

      Date now = new Date();
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss.SSS");
      String name = String.format(Locale.ENGLISH, "%s-%.09f", sdf.format(now), activity.imageTimestamp);
      File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                          "/TangoCamera");
      if (!dir.exists())
         dir.mkdirs();
      if (!dir.canWrite())
      {
         String message = "Cannot write to " + dir.getAbsolutePath() + " Trying ";
         dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "/TangoCamera");
         if (!dir.exists())
            dir.mkdirs();
         message = message + dir.getAbsolutePath();
         publishProgress(message);
         if (!dir.canWrite())
         {
            dir = new File("/sdcard/TangoCamera");
            dir.mkdirs();
            publishProgress("Falling back to " + dir.getAbsolutePath());
         }
      }
      try
      {
         publishProgress("Converting Image");
         File imageFile = new File(dir, name + ".jpg");
         if (! saveImage(imageFile))
            return false;

         publishProgress("Saving Details");
         File yamlFile = new File(dir, name + ".yaml");
         int w[] = new int[1], h[] =  new int[1];
         String yaml = saveYamlFile(yamlFile, w, h);
         int imagewidth = w[0], imageheight = h[0];

         publishProgress("Saving EXIF to " + imageFile.getName());
         saveEXIF(imageFile, yaml, imagewidth, imageheight);

         File plyFile = null;
         if ((activity.pointCloud != null) && (activity.noPoints > 0) && (ITango.isDepth()))
         {
            publishProgress("Saving PointCloud");
            plyFile = new File(dir, name + ".ply");
            savePlyFile(plyFile);
         }
         activity.pointCloud = null;
         activity.noPoints = 0;

         publishProgress("Save Complete");
         return true;
      }
      catch (Exception ee)
      {
         Log.e(TAG, "postProcess", ee);
         publishProgress("ERROR: Exception " + ee.getMessage() + " during post processing");
         errors.append(ee.getMessage()).append(" during post processing");
         return false;
      }
      finally
      {
         activity.imageData = null;
         activity.pointCloud = null;
         activity.pauseSensors(false);
      }
   }

   @Override
   protected void onProgressUpdate(String... values)
   //-----------------------------------------------
   {
      super.onProgressUpdate(values);
      activity.notification(values[0], TSnackbar.LENGTH_INDEFINITE, false);
   }

   @Override
   protected void onPostExecute(Boolean B)
   //-------------------------------------
   {
      activity.notification(null, 0, false);
      activity.showButtons();
      if (!B)
         activity.notification("Errors occurred during post processing:" + errors.toString(),
                               TSnackbar.LENGTH_INDEFINITE, true);
      else if (messages.length() > 0)
         activity.notification(messages.toString(), TSnackbar.LENGTH_INDEFINITE, false);
   }

   private boolean saveImage(File imageFile)
   //---------------------------------------
   {
      int imageFormat = activity.imageFormat, imageWidth = activity.imageWidth,
            imageHeight = activity.imageHeight;
      byte[] imageData = activity.imageData;
      final Bitmap bitmap;
      if ((imageFormat == ImageFormat.NV21) || (imageFormat == ImageFormat.YUV_420_888))
      {
         YuvImage yuvImage = new YuvImage(imageData, imageFormat, imageWidth, imageHeight, null);
         ByteArrayOutputStream os = new ByteArrayOutputStream();
         yuvImage.compressToJpeg(new Rect(0, 0, imageWidth, imageHeight), 100, os);
         publishProgress("Converting Image");
         byte[] jpegByteArray = os.toByteArray();
         bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);
      }
      else
      {
         for (int i = 0; i < imageData.length; i += 4)
         {
            byte b = imageData[i];
            imageData[i] = imageData[i + 3];
            imageData[i + 3] = b;
         }
         publishProgress("Converting Image");
         bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
         bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(imageData));
      }
      publishProgress("Saving Image");

      try (FileOutputStream fos = new FileOutputStream(imageFile))
      {
         bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
      }
      catch (Exception e)
      {
         Log.e(TAG, "PostProcessThread", e);
         publishProgress("ERROR: Exception " + e.getMessage() + " writing .jpeg image file");
         errors.append(e.getMessage()).append(" writing .jpeg image file");
         return false;
      }
      activity.imageData = null;
      activity.imageFormat = 0;
      return true;
   }

   private String saveYamlFile(File yamlFile, int w[], int h[])
   //----------------------------------------------------------
   {
      StringWriter sw = new StringWriter(4096);
      SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
      boolean isGravity = SP.getBoolean("gravity", true);
      boolean isAccel = SP.getBoolean("acceleration", true);
      try (PrintWriter pw = new PrintWriter(sw))
      {
         pw.println("# camera intrinsics");
         double fx[] = new double[1], fy[] = new double[1], cx[] = new double[1], cy[] = new double[1],
               hfov[] = new double[1], vfov[] = new double[1], distortion[] = new double[5];
         if (ITango.intrinsics(ITango.TangoCameraId.TANGO_CAMERA_COLOR.ordinal(), fx, fy, cx, cy, w, h,
                               hfov, vfov, distortion))
         {
            pw.println("# Intrinsics for default (color) camera");
            pw.printf("fx: %.9f", fx[0]); pw.println();
            pw.printf("fy: %.9f", fy[0]); pw.println();
            pw.printf("cx: %.9f", cx[0]); pw.println();
            pw.printf("cy: %.9f", cy[0]); pw.println();
            pw.printf("distortion: [%.9f, %.9f, %.9f, %.9f, %.9f]", distortion[0], distortion[1],
                      distortion[2], distortion[3], distortion[4]); pw.println();
            pw.printf("FOVh: %.9f", hfov[0]); pw.println();
            pw.printf("FOVv: %.9f", vfov[0]); pw.println();
            pw.printf("imagewidth: %d", w[0]); pw.println();
            pw.printf("imageheight: %d", h[0]); pw.println();
            pw.println();
         }
         double[] imuRotation = new double[4], imuTranslation = new double[3];
         if (ITango.IMU2CameraPose(ITango.TangoCameraId.TANGO_CAMERA_COLOR.ordinal(), imuRotation, imuTranslation))
         {
            pw.println("# IMU to camera pose for default (color) camera");
            writePose(pw, "IMU rotation quaternion [w, x, y, z] not corrected for device orientation",
                      "IMU rotation quaternion [w, x, y, z] corrected for device orientation",
                      "imuRawRotation", "imuRotation",
                       imuRotation[0], imuRotation[1], imuRotation[2], imuRotation[3],
                      "IMU translation [x, y, z] not corrected for device rotation",
                      "IMU translation [x, y, z] corrected for device rotation",
                      "imuRawTranslation", "imuTranslation",
                      imuTranslation[0], imuTranslation[1], imuTranslation[2]);
            pw.println();

         }
         if (ITango.intrinsics(ITango.TangoCameraId.TANGO_CAMERA_DEPTH.ordinal(), fx, fy, cx, cy, w, h,
                               hfov, vfov, distortion))
         {
            pw.println("# Intrinsics for depth camera");
            pw.printf("d_fx: %.9f", fx[0]); pw.println();
            pw.printf("d_fy: %.9f", fy[0]); pw.println();
            pw.printf("d_cx: %.9f", cx[0]); pw.println();
            pw.printf("d_cy: %.9f", cy[0]); pw.println();
            pw.printf("d_distortion: [%.9f, %.9f, %.9f, %.9f, %.9f]", distortion[0], distortion[1],
                      distortion[2], distortion[3], distortion[4]); pw.println();
            pw.printf("d_FOVh: %.9f", hfov[0]); pw.println();
            pw.printf("d_FOVv: %.9f", vfov[0]); pw.println();
            pw.printf("d_imagewidth: %d", w[0]); pw.println();
            pw.printf("d_imageheight: %d", h[0]); pw.println();
            pw.println();
         }
         if (ITango.IMU2CameraPose(ITango.TangoCameraId.TANGO_CAMERA_DEPTH.ordinal(), imuRotation, imuTranslation))
         {
            pw.println("# IMU to camera pose for depth camera");
            writePose(pw, "IMU rotation quaternion [w, x, y, z] not corrected for device orientation",
                      "IMU rotation quaternion [w, x, y, z] corrected for device orientation",
                      "d_imuRawRotation", "d_imuRotation",
                      imuRotation[0], imuRotation[1], imuRotation[2], imuRotation[3],
                      "IMU translation [x, y, z] not corrected for device rotation",
                      "IMU translation [x, y, z] corrected for device rotation",
                      "d_imuRawTranslation", "d_imuTranslation",
                      imuTranslation[0], imuTranslation[1], imuTranslation[2]);
            pw.println();

         }
         pw.println("# device rotation (0 = portrait for all phones and many tablets)");
         pw.print("deviceRotation: ");
         switch (activity.deviceRotation)
         {
            case Surface.ROTATION_0: pw.println("0"); break;
            case Surface.ROTATION_90: pw.println("90"); break;
            case Surface.ROTATION_180: pw.println("180"); break;
            case Surface.ROTATION_270: pw.println("270"); break;
         }

         writePose(pw, "pose rotation quaternion [w, x, y, z] not corrected for device orientation",
                   "pose rotation quaternion [w, x, y, z] corrected for device orientation",
                   "rawRotation", "rotation",
                   activity.rotationW, activity.rotationX, activity.rotationY, activity.rotationZ,
                   "pose translation [x, y, z] not corrected for device rotation",
                   "pose translation [x, y, z] corrected for device rotation",
                   "rawTranslation", "translation",
                   activity.translationX, activity.translationY, activity.translationZ);

         double [] vec = null;
         if (isGravity)
         {
            vec = findClosest(activity.gravityBuffer, activity.imageTimestamp);
            if (vec != null)
            {
               pw.println("# Raw Android gravity vector [x, y, z]");
               pw.printf("rawGravity: [%.9f, %.9f, %.9f]", vec[0], vec[1], vec[2]);
               pw.println();
               vec = correct(vec);
               pw.println("# Android gravity vector corrected for device rotation [x, y, z]");
               pw.printf("gravity: [%.9f, %.9f, %.9f]", vec[0], vec[1], vec[2]);
               pw.println();
            }
         }
         if (isAccel)
         {
            vec = findClosest(activity.accelBuffer, activity.imageTimestamp);
            if (vec != null)
            {
               pw.println("# Raw Android accelerometer vector [x, y, z]");
               pw.printf("rawAcceleration: [%.9f, %.9f, %.9f]", vec[0], vec[1], vec[2]);
               pw.println();
               vec = correct(vec);
               pw.println("# Android accelerometer vector corrected for device rotation [x, y, z]");
               pw.printf("acceleration: [%.9f, %.9f, %.9f]", vec[0], vec[1], vec[2]);
               pw.println();
            }
         }
      }
      try (BufferedWriter bw = new BufferedWriter(new FileWriter(yamlFile)))
      {
         bw.write(sw.toString());
      }
      catch (Exception e)
      {
         Log.e(TAG, "PostProcessThread", e);
         publishProgress("ERROR: Exception " + e.getMessage() + " writing .yaml file");
         messages.append(e.getMessage()).append(" writing .yaml file").append(", ");
         return null;
      }
      return sw.toString();
   }

   private void writePose(PrintWriter pw, String rawOrientationComment, String cookedOrientationComment,
                          String rawRotationKey, String cookedRotationKey,
                          double rotationW, double rotationX, double rotationY, double rotationZ,
                          String rawTranslationComment, String cookedTranslationComment,
                          String rawTranslationKey, String cookedTranslationKey,
                          double translationX, double translationY, double translationZ)
   //---------------------------------------------------------------------------------------------
   {
      float[] Rin, R = new float[16], Q;
      pw.println("# " + rawOrientationComment);
      pw.printf("%s: [%.9f, %.9f, %.9f, %.9f]", rawRotationKey, rotationW, rotationX, rotationY, rotationZ);
      pw.println();
      Rin = quaternionToMatrix((float) rotationW, (float) rotationX, (float) rotationY, (float) rotationZ);
      SensorManager.remapCoordinateSystem(Rin, androidXAxis, androidYAxis, R);
      Q = matrixToQuaternion(R);
      pw.println("# pose rotation quaternion [w, x, y, z] corrected for device orientation");
      pw.printf("%s: [%.9f, %.9f, %.9f, %.9f]", cookedRotationKey, Q[0], Q[1], Q[2], Q[3]);
      pw.println();
      pw.println("# " + rawTranslationComment);
      pw.printf("%s: [%.9f, %.9f, %.9f]", rawTranslationKey, translationX, translationY, translationZ);
      pw.println();
      pw.println("# " + cookedTranslationComment);
      double[] v = new double[] {translationX, translationY, translationZ};
      double[] T = correct(v);
      pw.printf("%s: [%.9f, %.9f, %.9f]",cookedTranslationKey, T[0], T[1], T[2]);
      pw.println();
   }

   private double[] correct(double[] vec)
   //------------------------------------
   {
      // Values from 0 to 9.xxx should fit in float, it seems like overkill to add la4j or jblas for a few matrix multiplies
      float[] raw = new float[4], cooked = new float[4];
      raw[0] = (float) vec[0]; raw[1] = (float) vec[1]; raw[2] = (float) vec[2]; raw[3] = 0;
      Matrix.multiplyMV(cooked, 0, IR, 0, raw, 0);
      vec[0] = cooked[0]; vec[1] = cooked[1]; vec[2] = cooked[2];
      return vec;
   }

   private boolean saveEXIF(File imageFile, String yaml, int imagewidth, int imageheight)
   //------------------------------------------------------------------------------------
   {
      ExifInterface exif = null;
      try
      {
         exif = new ExifInterface(imageFile.getAbsolutePath());
         exif.setAttribute("UserComment", yaml);
//         switch (activity.deviceRotation)
//         {
//            case Surface.ROTATION_90:
//               exif.setAttribute(ExifInterface.TAG_ORIENTATION,
//                                 Integer.toString(ExifInterface.ORIENTATION_ROTATE_90));
//               break;
//            case Surface.ROTATION_180:
//               exif.setAttribute(ExifInterface.TAG_ORIENTATION,
//                                 Integer.toString(ExifInterface.ORIENTATION_ROTATE_180));
//               break;
//            case Surface.ROTATION_270:
//               exif.setAttribute(ExifInterface.TAG_ORIENTATION,
//                                 Integer.toString(ExifInterface.ORIENTATION_ROTATE_180));
//               break;
//         }
         exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, Integer.toString(imagewidth));
         exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, Integer.toString(imageheight));
         exif.saveAttributes();
      }
      catch (Exception e)
      {
         Log.e(TAG, "PostProcessThread: Saving EXIF data", e);
         messages.append(e.getMessage()).append(" writing EXIF tags to ").append(imageFile.getName()).
               append(", ");
         return false;

      }
      activity.rotationW = activity.rotationX = activity.rotationY = activity.rotationZ =
      activity.translationX = activity.translationY = activity.translationZ = 0;
      return true;
   }

   private boolean savePlyFile(File plyFile)
   //---------------------------------------
   {
      SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
      float minConfidence;
      try
      {
         minConfidence = SP.getFloat("confidence", 0.5f);
      }
      catch (Exception e)
      {
         try
         {
            minConfidence = Float.parseFloat(SP.getString("confidence", "0.5"));
         }
         catch (Exception ee)
         {
            minConfidence = 0.5f;
         }
      }
      boolean writeConfidence = SP.getBoolean("write_confidence", false);
      FloatBuffer vertices = FloatBuffer.wrap(activity.pointCloud);
      float[] vertex = new float[4];
      StringWriter sw = new StringWriter(8192);
      int count = 0;
      try (PrintWriter pw = new PrintWriter(sw))
      {
         vertices.get(vertex);
         while (vertices.remaining() > 0)
         {
            if (vertex[3] >= minConfidence)
            {
               pw.printf("%.9f %.9f %.9f", vertex[0], vertex[1], vertex[2]);
               if (writeConfidence)
                  pw.printf(" %.9f", vertex[3]);
               pw.println();
               count++;
            }
            vertices.get(vertex);
         }
      }
      catch (BufferUnderflowException e)
      {
         Log.e(TAG, "Short read while writing " + plyFile.getName(), e);
      }
      catch (Exception e)
      {
         Log.e(TAG, "Writing .ply", e);
         publishProgress("ERROR: Exception " + e.getMessage() +
                               " saving point cloud .ply file");
         messages.append(e.getMessage()).append(" saving point cloud .ply file").append(", ");
         return false;
      }
      try (PrintWriter pw = new PrintWriter(plyFile))
      {
         pw.println("ply");
         pw.println("format ascii 1.0");
         pw.println("element vertex " + count);
         pw.println("property float x");
         pw.println("property float y");
         pw.println("property float z");
         if (writeConfidence)
         {
            pw.println("comment c is confidence probability");
            pw.println("property float c");
         }
         pw.println("end_header");
         pw.print(sw.toString());
      }
      catch (Exception e)
      {
         e.printStackTrace();
         messages.append(e.getMessage()).append(" saving point cloud .ply file").append(", ");
         return false;
      }
      return true;
   }

   static double[] findClosest(RingBuffer<double[]> buffer, double ts)
   //-----------------------------------------------------------------
   {
      double[][] values = buffer.popAll();
      int index = -1;
      double min = Double.MAX_VALUE;
      for (int i=0; i <values.length; i++)
      {
         double[] a = values[i];
         double m = abs(a[3] - ts);
         if (m < min)
         {
            min = m;
            index = i;
         }
      }
      if (index < 0)
         return null;
      return values[index];
   }

   static public float[] quaternionToMatrix(float w, float x, float y, float z)
   //-------------------------------------------------------------------------
   {
      float norm = w * w + x * x + y * y + z * z;
      float s = (norm > 0f) ? 2f / norm : 0;
      float xs = x * s;
      float ys = y * s;
      float zs = z * s;
      float xx = x * xs;
      float xy = x * ys;
      float xz = x * zs;
      float xw = w * xs;
      float yy = y * ys;
      float yz = y * zs;
      float yw = w * ys;
      float zz = z * zs;
      float zw = w * zs;
      float[] M = new float[16];
      M[0] = 1 - (yy + zz); M[4] = (xy - zw);     M[8] = (xz + yw);      M[12] = 0;
      M[1] = (xy + zw);     M[5] = 1 - (xx + zz); M[9] = (yz - xw);      M[13] = 0;
      M[2] = (xz - yw);     M[6] = (yz + xw);     M[10] = 1 - (xx + yy); M[14] = 0;
      M[3] = 0;             M[7] = 0;             M[11] = 0;             M[15] = 1;
      return M;
   }

   static public float[] matrixToQuaternion(float[] M)
   //-------------------------------------------------
   {
      float m00 = M[0], m01 = M[4], m02 = M[8];
      float m10 = M[1], m11 = M[5], m12 = M[9];
      float m20 = M[2], m21 = M[6], m22 = M[10];

      float t = m00 + m11 + m22, w, x, y, z;

      if (t >= 0)
      {
         float s = (float) Math.sqrt(t + 1);
         w = 0.5f * s;
         s = 0.5f / s;
         x = (m21 - m12) * s;
         y = (m02 - m20) * s;
         z = (m10 - m01) * s;
      }
      else if ((m00 > m11) && (m00 > m22))
      {
         float s = (float) Math.sqrt(1.0f + m00 - m11 - m22);
         x = s * 0.5f; // |x| >= .5
         s = 0.5f / s;
         y = (m10 + m01) * s;
         z = (m02 + m20) * s;
         w = (m21 - m12) * s;
      }
      else if (m11 > m22)
      {
         float s = (float) Math.sqrt(1.0f + m11 - m00 - m22);
         y = s * 0.5f; // |y| >= .5
         s = 0.5f / s;
         x = (m10 + m01) * s;
         z = (m21 + m12) * s;
         w = (m02 - m20) * s;
      }
      else
      {
         float s = (float) Math.sqrt(1.0f + m22 - m00 - m11); // |s|>=1
         z = s * 0.5f; // |z| >= .5
         s = 0.5f / s;
         x = (m02 + m20) * s;
         y = (m21 + m12) * s;
         w = (m10 - m01) * s;
      }
      return new float[] {w, x, y, z};
   }
}
