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
   parser.add_argument("-x", '--x', dest='x', type=float, default=float('nan'), help="Start x")
   parser.add_argument("-y", '--y', dest='y', type=float, default=float('nan'), help="Start y")
   parser.add_argument("-z", '--z', dest='z', type=float, default=float('nan'), help="Start z")
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
   IT = np.array([y['imuTranslation'][0], y['imuTranslation'][1], y['imuTranslation'][2]])
   IT = IT - np.array([y['d_imuTranslation'][0], y['d_imuTranslation'][1], y['d_imuTranslation'][2]])
   select(K, IT, plyfile, imgfile, args.x, args.y, args.z, basename, caxis)

def select(K, IT, plyfile, imgfile, xstart, ystart, zstart, basename, caxis=None):
   img = cv2.imread(imgfile)
   if img is None:
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

   P = np.eye(4)
   #P[0:3, 0:3] = IR
   P[0:3, 3] = IT
   cv2.namedWindow(basename)
   cv2.moveWindow(basename, 0, 0)
   no = 1
   for vertex in plydata['vertex']:
      x = vertex[0]
      if not m.isnan(xstart) and x < xstart:
         continue
      y = vertex[1]
      if not m.isnan(ystart) and y < ystart:
         continue
      z = vertex[2]
      if not m.isnan(zstart) and z < zstart:
         continue

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
      xy = K.dot(XX[0:3])
      xy = xy / xy[2]
      center = (int(xy[0]), int(xy[1]))
      img2 = img.copy()
      cv2.circle(img2, center, 3, color, 2)
      cv2.imshow(basename, img2)
      key = cv2.waitKey(0)
      if key == 10:
         name = "%s-%d" % (basename, no)
         cv2.imwrite(name + ".jpg", img2)
         with open(name + ".txt",  "w", encoding="utf-8") as f:
            f.write("(%.8f, %.8f, %.8f) = (%d, %d)" % (x, y, z, center[0], center[1]))
         no = no + 1
      elif key == 27:
         sys.exit(1)
   cv2.destroyAllWindows()
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

if __name__ == '__main__':
   sys.exit(main())
