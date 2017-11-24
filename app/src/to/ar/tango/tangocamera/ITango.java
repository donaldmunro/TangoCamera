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

import android.app.Activity;
import android.os.IBinder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ITango
//=================
{
   private static final String TAG = "CameraCalibration";

   public static native boolean create(Activity activity);

   public static native void destroy();

   public static native boolean serviceConnected(IBinder service);

   public static native boolean isServiceConnected();

   public static native boolean isDepth();

//   public static native void enableDepth(boolean b);

   public static native void disconnect();

   public static native boolean resetPoseStart();

   public static native boolean startTakingPhoto(boolean isPointCloud);

   public static native double lastTimestamp();

   public static native boolean intrinsics(int cameraId, double[] fx, double[] fy,
                                           double[] cx, double[] cy,
                                           int[] pixelHeight, int[] pixelWidth,
                                           double[] hFOV, double[] vFOV, double[] distortion);

   public static native boolean IMU2CameraPose(int cameraId, double[] rotation, double[] translation);

   public static native void renderInit();

   public static native void renderResize(int w, int h);

   public static native void render();

   public enum TangoCameraId { TANGO_CAMERA_COLOR, TANGO_CAMERA_RGBIR, TANGO_CAMERA_FISHEYE, TANGO_CAMERA_DEPTH,
                               TANGO_MAX_CAMERA_ID }

   private Map<TangoCameraId, CalibrationDetail> cameras = null;

   private void initCalibration()
   //----------------------------
   {
      double[] fx_ = new double[1], fy_ = new double[1], cx_ = new double[1], cy_ = new double[1],
               hfov_ = new double[1], vfov_ = new double[1], distortion_= new double[5];
      int[] width_ = new int[1], height_ = new int[1];
      for (TangoCameraId id : TangoCameraId.values())
      {
         int cameraid = id.ordinal();
         if (intrinsics(cameraid, fx_, fy_, cx_, cy_, height_, width_, hfov_, vfov_, distortion_))
         {
            if (cameras == null)
               cameras = new HashMap<>();
            cameras.put(id, new CalibrationDetail(cameraid, width_[0], height_[0], fx_[0], fy_[0],
                                                  cx_[0], cy_[0], hfov_[0], vfov_[0], distortion_));
         }
      }
   }

   public CalibrationDetail getCalibration(TangoCameraId id)
   {
      if (cameras == null)
         initCalibration();
      return cameras.get(id);
   }

   public CalibrationDetail getCalibration(int id)
   {
      if (cameras == null)
         initCalibration();
      return cameras.get(id);
   }


   public class CalibrationDetail
   //============================
   {
      private int cameraId, pixelWidth, pixelHeight;
      private double fx, fy, cx, cy, hFOV, vFOV;
      private double[] distortion = new double[5];
      private double[][] K;

      public CalibrationDetail(int cameraId, int pixelWidth, int pixelHeight, double fx, double fy,
                               double cx, double cy, double hFOV, double vFOV, double[] distortion)
      //-------------------------------------------------------------------------------------------
      {
         this.cameraId = cameraId;
         this.pixelWidth = pixelWidth;
         this.pixelHeight = pixelHeight;
         this.fx = fx;
         this.fy = fy;
         this.cx = cx;
         this.cy = cy;
         this.hFOV = hFOV;
         this.vFOV = vFOV;
         System.arraycopy(distortion, 0, this.distortion, 0, 5);
         K = new double[][] {  {  fx,  0,  cx },
                               {  0,   fy, cy },
                               {  0,   0,  0  }
                            };
      }

      public int cameraId() { return cameraId; }

      public double fx() { return fx; }

      public double fy() { return fy; }

      public double cx() { return cx; }

      public double cy() { return cy; }

      public int pixelWidth() { return pixelWidth; }

      public int pixelHeight() { return pixelHeight; }

      public double[] distortion() { return distortion; }

      public double hFOV() { return hFOV; }

      public double vFOV() { return vFOV; }

      public double[][] K() { return K; }

      @Override
      public String toString()
      //----------------------
      {
         return "CalibrationDetail{" +
               "cameraId=" + cameraId +
               ", fx=" + fx +
               ", fy=" + fy +
               ", cx=" + cx +
               ", cy=" + cy +
               ", pixelWidth=" + pixelWidth +
               ", pixelHeight=" + pixelHeight +
               ", hFOV=" + hFOV +
               ", vFOV=" + vFOV +
               ", distortion=" + Arrays.toString(distortion) +
               '}';
      }
   }

}
