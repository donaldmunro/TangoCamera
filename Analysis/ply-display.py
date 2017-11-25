import sys
import numpy as np
from mpl_toolkits.mplot3d import Axes3D
import matplotlib.pyplot as plt
try:
   from plyfile import PlyData, PlyElement
except:
   print('Install python-plyfile from https://github.com/dranjan/python-plyfile (using pip: pip install plyfile')
   sys.exit(1)
import time

def main(argv=None):
   if argv is None:
      argv = sys.argv
   if len(argv) != 2:
      print("Format ply-display ply-file.ply")
      sys.exit(1)

   try:
      plydata = PlyData.read(argv[1])
   except Exception as ex:
      print("Error reading ply file %s (%s)." % (argv[0], str(ex)), file=sys.stderr)
      sys.exit(1)
#   no = plydata['vertex'].count

   # According to the comment in https://github.com/googlesamples/tango-examples-c/blob/master/tango_client_api/include/tango_client_api.h:
   # "...+Z points in the direction of the camera's optical axis, perpendicular to the plane of the camera.
   # +X points toward the user's right, and +Y points toward the bottom of the screen. The origin is the focal center
   # of the depth camera."
   # i.e. it resembles that of OpenCV.
   # Convert to OpenGL looking down -Z axis with +X on right and +Y above use:
   #PCtoGL4x4 = np.array([ [ 1.0, 0.0, 0.0, 0.0 ] , [ 0.0, -1.0, 0.0, 0.0 ], [ 0.0, 0.0, -1.0, 0.0 ], [ 0.0, 0.0, 0.0, 1.0 ] ])
   PCtoGL = np.array([ [1.0, 0.0, 0.0], [0.0, -1.0, 0.0], [0.0, 0.0, -1.0] ])
   Xs = []
   Ys = []
   Zs = []
   for vertex in plydata['vertex']:
      X = PCtoGL.dot(np.array([vertex[0], vertex[1], vertex[2]]))
      Xs.append(X[0])
      Ys.append(X[1])
      Zs.append(X[2])
   figure = plt.figure()
   plot3d = figure.add_subplot(111, projection='3d')
   plot3d.scatter(Xs, Zs, Ys, c='r', marker='o')
   plot3d.set_xlabel('X')
   plot3d.set_ylabel('Z')
   plot3d.set_zlabel('Y')
   plt.show()

if __name__ == '__main__':
   sys.exit(main())
