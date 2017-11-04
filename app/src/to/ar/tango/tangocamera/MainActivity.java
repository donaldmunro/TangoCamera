package to.ar.tango.tangocamera;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaActionSound;
import android.media.ToneGenerator;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import com.androidadvance.topsnackbar.TSnackbar;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity
//=========================================
{
   private static final String TAG = "MainActivity";

   private Handler handler;
   private ServiceConnection tangoServiceConnection = new TangoServiceConnection();
   volatile private boolean isTangoConnected = false;

   private TextView initText;
   private GLSurfaceView surfaceView;
   ImageButton takePhotoButton;
   private FrameLayout frameLayout = null;
   private TSnackbar notifications = null;
   private SharedPreferences.OnSharedPreferenceChangeListener preferencesListener;
   private int hasCameraPerm = PackageManager.PERMISSION_DENIED;

   int deviceRotation;
   int imageFormat, imageWidth, imageHeight, imageStride, noPoints =-1;
   byte[] imageData = null;
   float[] pointCloud = null;
   double imageTimestamp, pointCloudTimestamp, rotationW, rotationX, rotationY, rotationZ,
          translationX, translationY, translationZ;
   RingBuffer<double[]> gravityBuffer = new RingBuffer<>(double[].class, 100),
                        accelBuffer = new RingBuffer<>(double[].class, 100);
   private Sensor gravitySensor = null, accelSensor = null;
   private SensorEvents sensorEventListener = null;
   private long localImageTimestamp, localPointcloudTimestamp;
   volatile private boolean isPauseSensors = false;

   @Override
   protected void onCreate(Bundle savedInstanceState)
   //------------------------------------------------
   {
      super.onCreate(savedInstanceState);
      deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
      hasCameraPerm = checkSelfPermission(Manifest.permission.CAMERA);
      setContentView(R.layout.activity_main);
      frameLayout = (FrameLayout) findViewById(R.id.main_view);
      initText = (TextView) findViewById(R.id.init_text);
      SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
      preferencesListener = new SharedPreferences.OnSharedPreferenceChangeListener()
      //------------------------------------------------------
      {
         @Override
         public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
         //-----------------------------------------------------------------------------------
         {
            if ( (key.equals("gravity")) || (key.equals("acceleration")) )
            {
               stopSensors();
               startSensors();
            }
         }
      };
      SP.registerOnSharedPreferenceChangeListener(preferencesListener);
      handler = new Handler();
   }

   private boolean isInitingTango = false;
   TangoLoader tangoLoader;

   private void initializeTango()
   //----------------------------
   {
      if (isInitingTango)
         return;
      isInitingTango = true;
      tangoLoader = new TangoLoader(this);
      handler.postDelayed(new Runnable() { @Override public void run() { tangoLoader.execute();  } }, 600);
   }

   void onTangoLoaded(boolean isOK, long starttimems, long endtimems, String message)
   //-------------------------------------------------------------------------------------------
   {
      long seconds = (endtimems - starttimems) / 1000;
      if (isOK)
      {
         isOK = ITango.create(MainActivity.this);
         if (isOK)
         {
            runOnUiThread(new Runnable()
            //==========================
            {
               @Override public void run()
               //------------------------
               {
                  initText.setText("Tango initialization took " + seconds + " seconds " +
                                   "\nPlease wait - Binding Tango Service");
               }
            });
            TangoInitializationHelper.bindTangoService(MainActivity.this, tangoServiceConnection);
         }
         else
            initText.setText("Error Initializing Tango - Version out of date\n" + message);
      }
      else
         initText.setText("Error Initializing Tango\n" + message);
   }

   private void onTangoBound()
   //-------------------------------
   {
      initText.setVisibility(View.GONE);
      showButtons();
      surfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);
      surfaceView.setVisibility(View.VISIBLE);
      surfaceView.setEGLContextClientVersion(3);
      surfaceView.setRenderer(new MainActivity.CameraRenderer());
      takePhotoButton = (ImageButton) findViewById(R.id.take_photo);
      frameLayout.invalidate();
      isInitingTango = false;
   }

   public void exit(View view)
   //-------------------------
   {
      hideButtons();
      ImageButton button = (ImageButton) findViewById(R.id.exeunt);
      button.setVisibility(View.GONE);
      if (surfaceView != null)
         surfaceView.setVisibility(View.GONE);
      initText.setText("Please wait - Disconnecting from Tango\n");
      initText.setVisibility(View.VISIBLE);
      frameLayout.forceLayout();
      frameLayout.invalidate();
      handler.postDelayed(new Runnable() { @Override public void run() { finish(); } }, 300);
   }

   @Override
   protected void onResume()
   //-----------------------
   {
      super.onResume();
      if ( (hasCameraPerm == PackageManager.PERMISSION_GRANTED) && (! isTangoConnected) )
         initializeTango();
      else
         if (hasCameraPerm != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] { Manifest.permission.CAMERA}, 1);
   }


   @Override
   protected void onDestroy()
   //------------------------
   {
      if (isTangoConnected)
      {
         try { ITango.destroy(); } catch (Throwable e) {}
         try
         {
            if (ITango.isServiceConnected())
            {
               unbindService(tangoServiceConnection);
               ITango.disconnect();
            }
         }
         catch (Throwable e)
         {}
      }
      super.onDestroy();
   }

   @Override
   public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
   //----------------------------------------------------------------------------------------------------------------
   {
      final int noGranted = grantResults.length;
      switch (requestCode)
      {
         case 1:
            if (noGranted > 0)
            {
               hasCameraPerm = grantResults[0];
               if (hasCameraPerm == PackageManager.PERMISSION_GRANTED)
                  initializeTango();
               else
                  finish();
            }
            break;
         case 2:
            if (noGranted > 0)
               clickedTakePhoto(null);
            else
               Toast.makeText(this, "Can't save output", Toast.LENGTH_LONG).show();
            break;
      }
   }

   private void startSensors()
   //-------------------------
   {
      SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
      sensorEventListener = new SensorEvents();
      SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
      boolean isGravity = SP.getBoolean("gravity", true);
      if (isGravity)
      {
         gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
         if (gravitySensor == null)
            notification("No Gravity sensor found (Android version < 2.2 ?).", TSnackbar.LENGTH_LONG, true);
         else
            sensorManager.registerListener(sensorEventListener, gravitySensor, SensorManager.SENSOR_DELAY_GAME);
      }
      boolean isAccel = SP.getBoolean("acceleration", true);
      if (isAccel)
      {
         accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
         if (accelSensor == null)
            notification("No Accelerometer sensor found.", TSnackbar.LENGTH_LONG, true);
         else
            sensorManager.registerListener(sensorEventListener, accelSensor, SensorManager.SENSOR_DELAY_GAME);
      }
   }

   private void stopSensors()
   //------------------------
   {
      SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
      if (sensorEventListener != null)
         sensorManager.unregisterListener(sensorEventListener);
      gravitySensor = accelSensor = null;
      gravityBuffer.clear(); accelBuffer.clear();
   }

   void pauseSensors(boolean isPause) { isPauseSensors = isPause; }

   private boolean isPointCloud = true;

   public void clickedTakePhoto(View view)
   //-------------------------------------
   {
      if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
      {
         requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
         return;
      }
      SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
      isPointCloud = SP.getBoolean("pointclouds", true);
//      startSensors();
      if (ITango.startTakingPhoto(isPointCloud))
         hideButtons();
      MediaActionSound sound = new MediaActionSound();
      sound.play(MediaActionSound.FOCUS_COMPLETE);
   }

   public void clickedSetOrigin(View view)
   //------------------------------------
   {
      if (ITango.resetPoseStart())
         notification("Origin reset OK", TSnackbar.LENGTH_SHORT, false);
      else
         notification("ERROR: Origin reset failed", TSnackbar.LENGTH_LONG, true);
   }

   public void requestRender()
   //-------------------------
   {
      if (surfaceView != null)
         surfaceView.requestRender();
   }

   public void onPhoto(int format, byte[] data, int width, int height, int stride,
                       double qw, double qx, double qy, double qz,
                       double tx, double ty, double tz, double timestamp)
   //------------------------------------------------------------------------------------
   {
      if ( (data != null) && (data.length > 0) )
      {
         imageFormat = format;
         imageWidth = width;
         imageHeight = height;
         imageStride = stride;
         imageData = data;
         imageTimestamp = timestamp;
         localImageTimestamp = SystemClock.elapsedRealtimeNanos();
         rotationW = qw;
         rotationX = qx;
         rotationY = qy;
         rotationZ = qz;
         translationX = tx;
         translationY = ty;
         translationZ = tz;
         pauseSensors(true);
//         stopSensors();
      }
   }

   public void onPointCloud(float[] data, double timestamp)
   //--------------------------------------------------------------
   {
      if ( (data != null) && (data.length > 0) )
      {
         pointCloud = data;
         noPoints = data.length;
         pointCloudTimestamp = timestamp;
         localPointcloudTimestamp = SystemClock.elapsedRealtimeNanos();
      }
   }

   PostProcessThread postProcessThread;

   public void postProcess()
   //---------------------------
   {
      if (postProcessThread != null)
      {
         try
         {
            postProcessThread.get(2, TimeUnit.SECONDS);
         }
         catch (Exception e)
         {
            Log.e(TAG, "", e);
            postProcessThread.cancel(true);
         }
      }
      postProcessThread = new PostProcessThread(this);
      postProcessThread.execute();
   }

   public void onTakePhotoError(final String message)
   //--------------------------------------------------
   {
      imageData = null;
      pointCloud = null;
      pauseSensors(false);
      handler.postDelayed(
      new Runnable()
      {
         @Override
         public void run()
         //--------------
         {
            notification(message, TSnackbar.LENGTH_INDEFINITE, true);
            takePhotoButton.setVisibility(View.VISIBLE);
            takePhotoButton.setEnabled(true);
            final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
         }
      }, 200);
   }

   public void clickedPrefs(View view)
   {
      Intent i = new Intent(this, PrefActivity.class);
      startActivity(i);
   }

   void notification(String message, int timeout, boolean isError)
   //---------------------------------------------------------------------
   {
      final String background, foreground;
      if (isError)
      {
         background = "#2B2B2B";
         foreground = "#FF0000";
      }
      else
         foreground = background = null;
      notification(message, timeout, null, background, foreground);
   }

   private synchronized void notification(final String message, final int timeout, final String backgroundColor,
                                          final String textBackgroundColor, final String textColor)
   //------------------------------------------------------------------------------------------------------
   {
      if (notifications != null)
         notifications.dismiss();
      notifications = null;
      if (message == null)
//                  v.setVisibility(View.GONE);
         return;
      notifications = TSnackbar.make(frameLayout, message, timeout);
      View v = notifications.getView();
      TextView t = (TextView) v.findViewById(com.androidadvance.topsnackbar.R.id.snackbar_text);
      if (backgroundColor != null)
         v.setBackgroundColor(Color.parseColor(backgroundColor));
      if (textBackgroundColor != null)
         t.setBackgroundColor(Color.parseColor(textBackgroundColor));
      if (textColor != null)
         t.setTextColor(Color.parseColor(textColor));
      notifications.setAction("DISMISS", new View.OnClickListener()
      {
         @Override public void onClick(View v)
         //----------------------------------
         {
            if (notifications != null)
               notifications.dismiss();
            notifications = null;
         }
      });
      notifications.show();
   }

   public void asyncNotification(final String message, final int timeout, boolean isError)
   //--------------------------------------------------------------------------------------
   {
      final String background, foreground;
      if (isError)
      {
         background = "#2B2B2B";
         foreground = "#FF0000";
      }
      else
         foreground = background = null;
      asyncNotification(message, timeout, null, null, null);
   }

   public void asyncNotification(final String message, final int timeout, final String backgroundColor,
                                 final String textBackgroundColor, final String textColor)
   //------------------------------------------------------------------------------------------------
   {
      runOnUiThread(new Runnable()
      //==========================
      {
         @Override public void run() { notification(message, timeout, backgroundColor, textBackgroundColor, textColor);}
      });
   }

   void showButtons()
   //---------------
   {
      ImageButton button = (ImageButton) findViewById(R.id.set_origin);
      button.setVisibility(View.VISIBLE);
      button.setEnabled(true);
      button = (ImageButton) findViewById(R.id.take_photo);
      button.setVisibility(View.VISIBLE);
      button.setEnabled(true);
      button = (ImageButton) findViewById(R.id.prefs);
      button.setVisibility(View.VISIBLE);
      button.setEnabled(true);
   }

   void hideButtons()
   //----------------
   {
      ImageButton button = (ImageButton) findViewById(R.id.set_origin);
      button.setEnabled(false);
      button.setVisibility(View.GONE);
      button = (ImageButton) findViewById(R.id.take_photo);
      button.setEnabled(false);
      button.setVisibility(View.GONE);
      button = (ImageButton) findViewById(R.id.prefs);
      button.setEnabled(false);
      button.setVisibility(View.GONE);
   }

   class CameraRenderer implements GLSurfaceView.Renderer
   //-------------------------------------------------------
   {
      public void onSurfaceCreated(GL10 gl, EGLConfig config)
      //------------------------------------------------------
      {
         ITango.renderInit();
         MainActivity.this.surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
      }

      public void onSurfaceChanged(GL10 gl, int width, int height) { ITango.renderResize(width, height); }

      public void onDrawFrame(GL10 gl) { ITango.render(); }
   }

   static class TangoLoader extends AsyncTask<Void, String, Boolean>
   //=================================================================
   {
      final private MainActivity activity;
      private boolean isLoading = false, isLoaded = false, ret = false;
      private StringBuilder messageBuf = new StringBuilder();
      private long starttime, endtime;

      public TangoLoader(MainActivity activity) { this.activity = activity; }

      @Override
      protected Boolean doInBackground(Void... voids)
      //---------------------------------------------
      {
         if (isLoading) return false;
         starttime = System.currentTimeMillis();
         isLoading = true;
         ret = false;
         this.publishProgress("Attempting load from Tango directories");
         try
         {
            isLoaded = (TangoInitializationHelper.loadTangoSharedLibrary() != null);
            if (! isLoaded)
               Log.e(TAG, "ERROR! Unable to load libtango_client_api.so!");
            else
               ret = true;
         }
         catch (Throwable e)
         {
            isLoaded = false;
            messageBuf.append("Exception " + e.getMessage() + " loading libtango_client_api.so\n");
         }
         if (! isLoaded)
         {
            this.publishProgress("Load from Tango directories failed. Loading from system directories");
            try
            {
               System.loadLibrary("tango_client_api");
               ret = isLoaded = true;
            }
            catch (Throwable ee)
            {
               Log.e(TAG, "ERROR!: Unable to load tango_client_api from system", ee);
               messageBuf.append("Exception " + ee.getMessage() + " loading tango_client_api from system libs\n");
               ret = false;
            }
         }
         if (isLoaded)
         {
            publishProgress("Loading application library (libtango.so)");
            try
            {
               System.loadLibrary("itango");
               ret = true;
            }
            catch (Throwable e)
            {
               Log.e(TAG, "ERROR! Unable to load libitango.so!", e);
               messageBuf.append("Exception " + e.getMessage() + " loading application library (libtango.so)\n");
               ret = isLoaded = false;
            }
         }
         endtime = System.currentTimeMillis();
         isLoading = false;
         return ret;
      }

      @Override
      protected void onProgressUpdate(String... messages)
      {
         activity.initText.setText(activity.initText.getText() + "\n" + messages[0]);
      }

      @Override
      protected void onPostExecute(Boolean aBoolean)
      {
         activity.onTangoLoaded(ret, starttime, endtime, messageBuf.toString());
      }
   };

   class TangoServiceConnection implements ServiceConnection
   //=======================================================
   {
      public void onServiceConnected(ComponentName name, IBinder service)
      //-----------------------------------------------------------------
      {
         if (service == null)
         {
            notification("Error connecting to Tango: null binder", TSnackbar.LENGTH_LONG, true);
            Log.e(TAG, "null binder");
            return;
         }
         try
         {
            if (!ITango.serviceConnected(service))
            {
               Log.e(TAG, "Tango service connection error");
               notification("Tango service connection error", TSnackbar.LENGTH_LONG, true);
               return;
            }
         }
         catch (Throwable e)
         {
            Log.e(TAG, "serviceConnected", e);
            notification("serviceConnected" + e.getMessage(), TSnackbar.LENGTH_LONG, true);
            return;

         }
         isTangoConnected = true;
         startSensors();
         onTangoBound();
//         for (ITango.TangoCameraId id : ITango.TangoCameraId.values())
//         {
//            ITango.CalibrationDetail detail = iTango.getCalibration(id);
//            if (detail != null)
//               Log.i(TAG, detail.toString());
//         }
      }

      public void onServiceDisconnected(ComponentName name) { isTangoConnected = false; }
   }

   class SensorEvents implements SensorEventListener
   //===============================================
   {
      private static final float NS2S = 1.0f / 1000000000.0f;
      double lastGravityTs =-2, lastAccelTs = -2;

      @Override
      public void onSensorChanged(SensorEvent event)
      //---------------------------------------------
      {
         if ( (isPauseSensors) || (! isTangoConnected) )
            return;
         double[] values;
         float[] eventValues = event.values;
         double tangoTime = ITango.lastTimestamp();
         if (tangoTime < 0)
            return;
         switch (event.sensor.getType())
         {
            case Sensor.TYPE_GRAVITY:
               if (tangoTime == lastGravityTs)
                  return;
               lastGravityTs = tangoTime;
               values = new double[5];
               values[0] = eventValues[0];
               values[1] = eventValues[1];
               values[2] = eventValues[2];
               values[3] = tangoTime;
               values[4] = event.timestamp * NS2S;
               gravityBuffer.push(values);
               break;
            case Sensor.TYPE_ACCELEROMETER:
               if (tangoTime == lastAccelTs)
                  return;
               lastAccelTs = tangoTime;
               values = new double[5];
               values[0] = eventValues[0];
               values[1] = eventValues[1];
               values[2] = eventValues[2];
               values[3] = tangoTime;
               values[4] = event.timestamp * NS2S;
               accelBuffer.push(values);
               break;
         }
      }

      @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
   }
}
