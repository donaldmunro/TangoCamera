import sys
import argparse
import os.path
import math as m
import cv2
import numpy as np
import yaml
import traceback
try:
   import quaternion
except:
   print('Install numpy-quaternion %s (%s) (which also requires scipy and optionally numba)' %
         ("pip3 install numpy-quaternion",  "https://github.com/moble/quaternion"))
   sys.exit(1)
try:
   from plyfile import PlyData, PlyElement
except:
   print('Install python-plyfile from https://github.com/dranjan/python-plyfile (using pip: pip install plyfile')
   sys.exit(1)

from typing import List
from typing import Tuple
Vector = List[float]
SList = List[str]
STuple = Tuple[str]

def main(argv=None):
   if argv is None:
      argv = sys.argv
   files_help = """
The yaml file from TangoCamera. Optionally also specify the ply file and/or
the jpg file if their basenames differ from the yaml file.
If the file basenames are the same then only the basename (without the yaml
extension) needs to be specified without specifying any other specific filenames.
   """
   parser = argparse.ArgumentParser(description='Project 3d points from ply file back onto image.')
   parser.add_argument("-c", '--caxis', dest='caxis',
                       help="Axis to color code points from (x, y or z) . Default is none")
   parser.add_argument('files', metavar='{file.yaml [file.ply] [file.jpg]}', nargs='+',
                       help=files_help)
   try:
      args = parser.parse_args()
   except:
      try:
         parser.print_help()
      except:
         print('project {files} ', file=sys.stderr)
         print('{files} :', files_help, file=sys.stderr)
      sys.exit(1)
   print(args)
   if args.caxis:
      caxis = args.caxis.lower().strip()
      if caxis != 'y' and caxis != 'x' and caxis != 'z':
         print("Axis to color code (%s) must be one of x, y or z" % (caxis,), file=sys.stderr)
         sys.exit(1)
   else:
      caxis = None
   yamlfile, imgfile, plyfile, basename = process_files(args.files)
   print(yamlfile, imgfile, plyfile)
   y = None
   try:
      with open(yamlfile, "r", encoding="utf-8") as f:
         y = yaml.load(f)
   except Exception as ex:
      print('Error opening yaml file %s (%s)' % (yamlfile, str(ex)), file=sys.stderr)
      sys.exit(1)
   K = np.array([[y['fx'], 0, y['cx']], [0, y['fy'], y['cy']], [0, 0, 1]])
#   dK = np.array([[y['d_fx'], 0, y['d_cx']], [0, y['d_fy'], y['d_cy']], [0, 0, 1]])
#   sdK = np.array([[y['imagewidth']/y['d_imagewidth'], 0, 0], [0, y['imageheight']/y['d_imageheight'], 0], [0, 0, 1]])
#   dK = sdK.dot(dK)
#   IT = np.array([ y['imuRawTranslation'][0], y['imuRawTranslation'][1], y['imuRawTranslation'][2] ])
   IT = np.array([y['imuTranslation'][0], y['imuTranslation'][1], y['imuTranslation'][2]])
   IT = IT - np.array([y['d_imuTranslation'][0], y['d_imuTranslation'][1], y['d_imuTranslation'][2]])

#   Q = np.quaternion(y['imuRawRotation'][0], y['imuRawRotation'][1], y['imuRawRotation'][2], y['imuRawRotation'][3])
#   Eu = quaternion.as_euler_angles(Q)
#   print(quaternionAxisAngle(Q))
#   print('Euler imuRotation: ', m.degrees(Eu[0]), m.degrees(Eu[1]), m.degrees(Eu[1]))

   distort = np.array([ y['distortion'][0], y['distortion'][1], y['distortion'][2], y['distortion'][3], y['distortion'][4] ])
#   img = project(K, IT, distort, plyfile, imgfile)
   img = project(K, IT, None, plyfile, imgfile, caxis)
   if img is not None:
      cv2.imwrite(basename + "-project.jpg", img)
      cv2.imshow(os.path.basename(imgfile), img)
      cv2.waitKey(0)
      cv2.destroyAllWindows()

def project(K, IT, distort, plyfile, imgfile, caxis=None):
   src = cv2.imread(imgfile)
   if src is None:
      print("Error reading image file %s" % (imgfile,))
      return None
   try:
      plydata = PlyData.read(plyfile)
   except Exception as ex:
      print("Error reading ply file %s (%s)." % (plyfile, str(ex)), file=sys.stderr)
      return None
   if caxis is not None:
      import pandas as pd
      vertex_data = pd.DataFrame(plydata['vertex'].data)
#      maxx, maxy, maxz = vertex_data.max()
#      minx, miny, minz = vertex_data.min()
#      meanx, meany, meanz = vertex_data.mean()
      quarters = vertex_data.quantile(.25)
      halfs = vertex_data.quantile(.5)
      three_quarters = vertex_data.quantile(.75)

   if distort is not None:
      h, w = src.shape[:2]
      NK, roi = cv2.getOptimalNewCameraMatrix(K, distort, (w, h), 0)
      mapx, mapy = cv2.initUndistortRectifyMap(K, distort, None, NK, (w, h), 5)
      img = cv2.remap(src, mapx, mapy, cv2.INTER_LINEAR)
      #   print(K, NK)
      #   img = cv2.undistort(src, K, distort, None, NK)
   else:
      img = src
      NK = K

   P = np.eye(4)
   #P[0:3, 0:3] = IR
   P[0:3, 3] = IT

   for vertex in plydata['vertex']:
      x = vertex[0]
      y = vertex[1]
      z = vertex[2]
      #X = np.array([vertex[0], vertex[1] + 0.011, vertex[2], 1])
      X = np.array([x, y, z, 1])

      if caxis is not None:
         if caxis == 'x':
            v = x
            ci = 0
         elif caxis == 'z':
            v = z
            ci = 2
         else:
            v = y
            ci = 1
         if v <= quarters[ci]:
            color = (255, 0, 0)
         elif quarters[ci] < v <= halfs[ci]:
            color = (0, 255, 0)
         elif halfs[ci] < v <= three_quarters[ci]:
            color = (0, 255, 255)
         else:
            color = (0, 0, 255)
      else:
         color = (0, 0, 0)

      XX = P.dot(X)
      xy = NK.dot(XX[0:3])
      xy = xy / xy[2]
      center = (int(xy[0]), int(xy[1]))
      cv2.circle(img, center, 2, color, 2)
   return img

def process_files(files: SList) ->STuple:
   basename = yamlfile = imgfile = plyfile = ''
   if len(files) == 1:
      filename = os.path.basename(files[0])
      dir = os.path.dirname(os.path.abspath(files[0]))
      if filename.endswith('.'):
         basename, ext = os.path.splitext(os.path.basename(files[0]))
      else:
         basename = filename
      yamlfile = os.path.join(dir, basename + ".yaml")
      imgfile = os.path.join(dir, basename + ".jpg")
      plyfile = os.path.join(dir, basename + ".ply")
   else:
      for filename in files:
         dir = os.path.dirname(os.path.abspath(filename))
         name, ext = os.path.splitext(os.path.basename(filename))
         if ext == '.yaml':
            yamlfile = os.path.join(dir, name + ".yaml")
            if len(basename) == 0:
               basename = name
         elif ext == '.jpg':
            imgfile = os.path.join(dir, name + ".jpg")
            if len(basename) == 0:
               basename = name
         elif ext == '.ply':
            plyfile = os.path.join(dir, name + ".ply")
            if len(basename) == 0:
               basename = name
   if len(basename) > 0:
      if len(yamlfile) == 0:
         yamlfile = os.path.join(dir, basename + ".yaml")
      if len(imgfile) == 0:
         imgfile = os.path.join(dir, name + ".jpg")
      if len(plyfile) == 0:
         plyfile = os.path.join(dir, name + ".ply")
   if not os.path.exists(yamlfile):
      print("Yaml file %s not found." % (yamlfile,), file=sys.stderr)
      return ("", "", "")
   if not os.path.exists(imgfile):
      print("Image file %s not found." % (imgfile,), file=sys.stderr)
      return ("", "", "")
   if not os.path.exists(plyfile):
      print("Ply file %s not found." % (plyfile,), file=sys.stderr)
      return ("", "", "")
   return (yamlfile, imgfile, plyfile, basename)

def quaternion_to_euler_angle(Q):
   w = Q.w
   x = Q.x
   y = Q.y
   z = Q.z

   ysqr = y * y

   t0 = +2.0 * (w * x + y * z)
   t1 = +1.0 - 2.0 * (x * x + ysqr)
   X = m.atan2(t0, t1)

   t2 = +2.0 * (w * y - z * x)
   t2 = +1.0 if t2 > +1.0 else t2
   t2 = -1.0 if t2 < -1.0 else t2
   Y = m.asin(t2)

   t3 = +2.0 * (w * z + x * y)
   t4 = +1.0 - 2.0 * (ysqr + z * z)
   Z = m.atan2(t3, t4)

   return X, Y, Z

def rotation2Euler(R):
   sy = m.sqrt(R[0,0] * R[0,0] +  R[1,0] * R[1,0] )
   singular = sy < 1e-6
   if not singular:
      x = m.atan2(R[2,1] , R[2,2])
      y = m.atan2(-R[2,0], sy);
      z = m.atan2(R[1,0], R[0,0])
   else:
      x = m.atan2(-R[1,2], R[1,1])
      y = m.atan2(-R[2,0], sy)
      z = 0
   return x, y, z

def quaternionAxisAngle(q):
   Q = q.normalized()
   angle = 2 * m.acos(Q.w)
   s = m.sqrt(1-Q.w * Q.w)
   if s < 0.001:
      x = Q.x
      y = Q.y;
      z = Q.z;
   else:
      x = Q.x / s
      y = Q.y / s
      z = Q.z / s
   return (angle, np.array([x, y, z]))


def quaternion_matrix(Q, size=3):
   q = np.array([Q.w, Q.x, Q.y, Q.z])
   n = np.dot(q, q)
   if n < np.finfo(float).eps * 4.0:
      return np.identity(4)
   q *= m.sqrt(2.0 / n)
   q = np.outer(q, q)
   if size == 3:
      return np.array([
         [1.0 - q[2, 2] - q[3, 3], q[1, 2] - q[3, 0], q[1, 3] + q[2, 0]],
         [q[1, 2] + q[3, 0], 1.0 - q[1, 1] - q[3, 3], q[2, 3] - q[1, 0]],
         [q[1, 3] - q[2, 0], q[2, 3] + q[1, 0], 1.0 - q[1, 1] - q[2, 2]]])
   else:
      return np.array([
         [1.0 - q[2, 2] - q[3, 3], q[1, 2] - q[3, 0], q[1, 3] + q[2, 0], 0.0],
         [q[1, 2] + q[3, 0], 1.0 - q[1, 1] - q[3, 3], q[2, 3] - q[1, 0], 0.0],
         [q[1, 3] - q[2, 0], q[2, 3] + q[1, 0], 1.0 - q[1, 1] - q[2, 2], 0.0],
         [0.0, 0.0, 0.0, 1.0]])


AXIS_X = 1
AXIS_Y = 2
AXIS_Z = 3
AXIS_MINUS_X = AXIS_X | 0x80
AXIS_MINUS_Y = AXIS_Y | 0x80
AXIS_MINUS_Z = AXIS_Z | 0x80

def androidRemapCoordinateSystem(R, X, Y):
#  Translated from SensorManager.remapCoordinateSystem
   length = np.size(R)
   rows = np.size(R, 0)
   cols = np.size(R, 1)
   if (rows != 3) and (rows != 4) and (cols != 3) and (cols != 4):
      return None
   if (X & 0x7C) != 0 or (Y & 0x7C) != 0:
      return None
   if ((X & 0x3) == 0) or ((Y & 0x3) == 0):
      return None
   if (X & 0x3) == (Y & 0x3):
      return None

   # Z is "the other" axis, its sign is either +/- sign(X)*sign(Y)
   # this can be calculated by exclusive-or'ing X and Y; except for
   # the sign inversion (+/-) which is calculated below.
   Z = X ^Y

   # extract the axis (remove the sign), offset in the range 0 to 2.
   x = (X & 0x3) - 1
   y = (Y & 0x3) - 1
   z = (Z & 0x3) - 1

   # compute the sign of Z (whether it needs to be inverted)
   axis_y = (z + 1) % 3
   axis_z = (z + 2) % 3
   if ((x ^ axis_y) | (y ^ axis_z)) != 0:
      Z ^= 0x80

   sx = (X >= 0x80)
   sy = (Y >= 0x80)
   sz = (Z >= 0x80)

   outR = np.zeros((rows, cols))
   for j in range(0, 3):
      icol = R[:, j]
      ocol = outR[:, j]
      for i in range(0, 3):
         if x == i:
            if sx:
               ocol[i] = -icol[0]
            else:
               ocol[i] = icol[0]
         if y == i:
            if sy:
               ocol[i] = -icol[1]
            else:
               ocol[i] = icol[1]
         if z == i:
            if sz:
               ocol[i] = -icol[2]
            else:
               ocol[i] = icol[2]
   if cols == 4:
      outR[3, 3] = 1
   return outR

def androidRemapCoordinateSystemMatrix(R, deviceRotation):
   global AXIS_X, AXIS_Y, AXIS_Z, AXIS_MINUS_X, AXIS_MINUS_Y, AXIS_MINUS_Z
   if deviceRotation == 90:
      androidXAxis = AXIS_Y
      androidYAxis = AXIS_MINUS_X
   elif deviceRotation ==180:
      androidXAxis = AXIS_MINUS_X
      androidYAxis = AXIS_MINUS_Y
   elif deviceRotation == 270:
      androidXAxis = AXIS_MINUS_Y
      androidYAxis = AXIS_X
   else:
      androidXAxis = AXIS_X
      androidYAxis = AXIS_Y
   return androidRemapCoordinateSystem(R, androidXAxis, androidYAxis)

def androidRemapCoordinateSystemVector(v, deviceRotation):
   global AXIS_X, AXIS_Y, AXIS_Z, AXIS_MINUS_X, AXIS_MINUS_Y, AXIS_MINUS_Z
   I = np.eye(np.size(v))
   if deviceRotation == 90:
      androidXAxis = AXIS_Y
      androidYAxis = AXIS_MINUS_X
   elif deviceRotation ==180:
      androidXAxis = AXIS_MINUS_X
      androidYAxis = AXIS_MINUS_Y
   elif deviceRotation == 270:
      androidXAxis = AXIS_MINUS_Y
      androidYAxis = AXIS_X
   else:
      androidXAxis = AXIS_X
      androidYAxis = AXIS_Y
   R = androidRemapCoordinateSystem(I, androidXAxis, androidYAxis)
   return R.dot(v)

if __name__ == '__main__':
   sys.exit(main())
