import argparse
import sys
import os
import io
import math
import os.path
import tempfile
import numpy as np

try:
   from plyfile import PlyData, PlyElement
except:
   print('Install python-plyfile from https://github.com/dranjan/python-plyfile (using pip: pip install plyfile')
   sys.exit(1)

def main(argv=None):
   if argv is None:
      argv = sys.argv
   parser = argparse.ArgumentParser(description='Filter points from .ply files',
                                    usage="""
                                    ply-filter [options] [file.ply].
                                    If file.ply not specified or - specified then read input from stdin.
                                    Output will be sent to stdout unless option -o specified.
                                    Example: ply-filter -X 0.4 -x 0.5 file.ply
                                             cat file.ply | ply-filter -X 0.4 -x 0.5
                                    Filters (removes) x vertex values between 0.4 and 0.5
                                    ply-filter -i -X 0.4 -x 0.5 file.ply
                                    Keeps only x vertex values between 0.4 and 0.5
                                    """)
   parser.add_argument("-i", '--include', dest='include',action="store_true", default=False,
   help="Include only values matching >, >=, <, <=. Default is to exclude.")
   parser.add_argument("-X", '--Xg', dest='xg', type=float, default=float('nan'),
   help="Filter x values greater than argument")
   parser.add_argument("-x", '--xl', dest='xl', type=float, default=float('nan'),
   help="Filter x values less than argument")
   parser.add_argument("-Y", '--Yg', dest='yg', type=float, default=float('nan'),
   help="Filter y values greater than argument")
   parser.add_argument("-y", '--yl', dest='yl', type=float, default=float('nan'),
   help="Filter y values less than argument")
   parser.add_argument("-Z", '--Zg', dest='zg', type=float, default=float('nan'),
   help="Filter z values greater than argument")
   parser.add_argument("-z", '--zl', dest='zl', type=float, default=float('nan'),
   help="Filter z values less than argument")

   parser.add_argument('--Xe', dest='xge', type=float, default=float('nan'),
   help="Filter x values >= than argument")
   parser.add_argument("--xe", dest='xle', type=float, default=float('nan'),
   help="Filter x values <= argument")
   parser.add_argument("--Ye", dest='yge', type=float, default=float('nan'),
   help="Filter y values >= argument")
   parser.add_argument("--ye", dest='yle', type=float, default=float('nan'),
   help="Filter y values <= argument")
   parser.add_argument('--Ze', dest='zge', type=float, default=float('nan'),
   help="Filter z values greater than argument")
   parser.add_argument('--ze', dest='zle', type=float, default=float('nan'),
   help="Filter z values less than argument")

   parser.add_argument("-o", '--output', dest='out', help="Output file")

   parser.add_argument('ply', metavar='{file.ply}', nargs='?', default='-',
                       help='The .ply file. Empty or - to process standard input')
   args = parser.parse_args()
#   print(args)
   if (not math.isnan(args.xg) and not math.isnan(args.xge)) or (not math.isnan(args.xl) and not math.isnan(args.xle)):
      print("Only one of -X, -Xe or -x, --xe allowed", file=sys.stderr)
   if (not math.isnan(args.yg) and not math.isnan(args.yge)) or (not math.isnan(args.yl) and not math.isnan(args.yle)):
      print("Only one of -Y, -Ye or -y, --ye allowed", file=sys.stderr)
   if (not math.isnan(args.zg) and not math.isnan(args.zge)) or (not math.isnan(args.zl) and not math.isnan(args.zle)):
      print("Only one of -Z, -Ze or -z, --ze allowed", file=sys.stderr)
   s = None
   source = dest = None
   if (args.out):
      dest = os.path.abspath(args.out)
      if not os.access(os.path.dirname(dest), os.W_OK):
         print("%s not a directory or not writable" % (os.path.dirname(dest),), file=sys.stderr)
         sys.exit(1)
   try:
      if args.ply and args.ply.strip() != '-':
         source = args.ply
         if os.path.exists(source):
            with open(source, "r", encoding="utf-8") as f:
               s = ''.join(f.readlines())
#            if dest is None:
#               name,ext = os.path.splitext(os.path.basename(source))
#               dest = os.path.join(os.path.dirname(os.path.abspath(source)), name + '-filter' + ext)
      else:
         source = 'stdin'
         s = ''.join(sys.stdin.readlines())
   except:
      s = None
   if s is None or len(s.strip()) == 0:
      print("Error opening %s" % (source,), file=sys.stderr)
      sys.exit(1)
   f = io.BytesIO(s.encode())
   try:
      plydata = PlyData.read(f)
   except:
      plydata = None
   if plydata is None or len(plydata) == 0:
      print("Error reading %s" % (source,), file=sys.stderr)
      sys.exit(1)
   CompareX = RangeComparison(args.xg, args.xge, args.xl, args.xle, args.include)
   CompareY = RangeComparison(args.yg, args.yge, args.yl, args.yle, args.include)
   CompareZ = RangeComparison(args.zg, args.zge, args.zl, args.zle, args.include)
   a = np.copy(plydata['vertex'][0:0].data)
   for vertex in plydata['vertex']:
      x = vertex[0]
      if not CompareX.accept(x):
         continue
      y = vertex[1]
      if not CompareY.accept(y):
         continue
      z = vertex[2]
      if not CompareZ.accept(z):
         continue
      # TODO: This won't break any performance records - could be improved for large ply files
      a = np.append(a, np.array([(x, y, z)], dtype=a.dtype))
   f.close()
   el = PlyElement.describe(a, plydata.elements[0].name)
   if dest is None:
#      PlyData([el], text=True).write(sys.stdout)
      tmpname = tempfile.mktemp()
      try:
            PlyData([el], text=True).write(tmpname)
            with open(tmpname, "r", encoding="utf-8") as f:
               s = ''.join(f.readlines())
            sys.stdout.write(s)
      except Exception as ex:
         print("Exception writing output (%s)" % (str(ex),), file=sys.stderr)
      if os.path.exists(tmpname):
         os.remove(tmpname)
   else:
      PlyData([el], text=True).write(dest)

class RangeComparison(object):
   def __init__(self, g, ge, l, le, inclusive):
      self.g = sys.float_info.min
      self.l = sys.float_info.max
      self.closed = False
      self.inclusive = inclusive
      self.compare_greater = self.greater
      self.compare_less = self.less
      if not math.isnan(g):
         self.g = g
      elif not math.isnan(ge):
         self.g = ge
         self.compare_greater = self.greater_or_equal
      if not math.isnan(l):
         self.l = l
      elif not math.isnan(le):
         self.l = le
         self.compare_less = self.less_or_equal

   def greater(self, v):
      return v > self.g

   def greater_or_equal(self, v):
      return v >= self.g

   def less(self, v):
      return v < self.l

   def less_or_equal(self, v):
      return v <= self.l

   def accept(self, v):
      inside = (self.compare_greater(v) and self.compare_less(v))
      if self.inclusive:
         return inside
      else:
         return not inside

if __name__ == '__main__':
   sys.exit(main())

