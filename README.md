# TangoCamera
**New 2017-11-21**: Changed YAML file/EXIF tag to also include:
- Depth camera intrinsics,
- Pose rotation corrected for device orientation,
- Pose translation corrected for device orientation.

TangoCamera is an open source developers tool for saving images,
pointclouds and associated Tango pose and Android sensor data which may
be used for Computer Vision and Augmented Reality research,
particularly when developing or validating pose finding algorithms.
Obviously it only runs on Tango supporting devices (the original Google tablet, the Asus Zenfone AR and the Lenovo Phab2) and has only so far been tested on a Zenfone AR.

The default settings will, when taking a photograph,

1. Store the JPEG image (with the YAML string encoded into the EXIF UserComment tag),
2. A YAML file containing:
    - The camera intrinsics (fx, fy, cx, cy, distortion, fovx, fovy)
    - The device rotation (0, 90, 180, 270 where 0 = portrait for phones
 and many but not all tablets).
    - Tango pose rotation quaternion and Tango pose translation vector
    - Android gravity vector (raw and corrected for device rotation),
3. The associated pointcloud as a .ply file (vertices only).

The settings button in the app allows modification to the default settings including disabling storage of pointclouds, specifying minimum confidence probabilities for point cloud vertices to be stored, whether the confidence value should be stored as a 4th point in the .ply file and whether to also store the Android accelerometer sensor vectors.

The only other element of the UI requiring some explanation is the leftmost button which can be used to reset the Tango pose origin (calls TangoService_resetMotionTracking).

TangoCamera is available from the Google Play Store at [https://play.google.com/store/apps/details?id=to.ar.tango.tangocamera&hl=en](https://play.google.com/store/apps/details?id=to.ar.tango.tangocamera&hl=en)
