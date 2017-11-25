import sys
import argparse
from operator import itemgetter, attrgetter
import numpy as np
from mpl_toolkits.mplot3d import Axes3D
import matplotlib.pyplot as plt
try:
   from plyfile import PlyData, PlyElement
except:
   print('Install python-plyfile from https://github.com/dranjan/python-plyfile (using pip: pip install plyfile')
   sys.exit(1)

def main(argv=None):
   if argv is None:
      argv = sys.argv
   parser = argparse.ArgumentParser(description='Sort .ply file vertices.',
                                    usage="Example: python ply-sort.py -a z input.yaml output.yaml")
   parser.add_argument("-a", '--axis', dest='axis', default=None,
                       help="Axis or axes to sort on (x, y, z or combinations of x, y and z). ")
   parser.add_argument("-d", '--descending', dest='descending', action="store_true", default=False,
                       help="Descending sort")
   parser.add_argument('files', nargs=2,# metavar=('input.yaml', 'output.yaml'),
                       help="""The input file followed by the output file.""")
   try:
      args = parser.parse_args()
   except:
      parser.print_help()
      sys.exit(1)
   if args.axis:
      axiss = args.axis.strip().upper()
   else:
      print('Specify axis to sort using -a (--axis) eg -a X to sort on the X axis or -a zx to sort on Z followed by X.')
      sys.exit(1)
   axes = []
   for axis in axiss:
      if axis == 'X':
         if axes.count(0) > 0:
            print('WARNING" Ignoring multiple occurrences of X in axis argument %s' % (axiss,) )
         else:
            axes.append(0)
      elif axis == 'Y':
         if axes.count(1) > 0:
            print('WARNING" Ignoring multiple occurrences of Y in axis argument %s' % (axiss,) )
         else:
            axes.append(1)
      elif axis == 'Z':
         if axes.count(2) > 0:
            print('WARNING" Ignoring multiple occurrences of Z in axis argument %s' % (axiss,) )
         else:
            axes.append(2)
      else:
         print('Invalid axis %s specified in axis string %s.' % (axis, axiss))
         sys.exit(1)
   try:
      plydata = PlyData.read(args.files[0])
   except Exception as ex:
      print("Error reading ply file %s (%s)." % (argv[0], str(ex)), file=sys.stderr)
      sys.exit(1)

   sorted_vertices = sorted(plydata['vertex'].data, key=itemgetter(*axes), reverse=args.descending)
   a = np.copy(plydata['vertex'][0:0].data)
   for vertex in sorted_vertices:
      a = np.append(a, np.array([(vertex[0], vertex[1], vertex[2])], dtype=a.dtype))
   el = PlyElement.describe(a, plydata.elements[0].name)
   PlyData([el], text=True).write(args.files[1])

if __name__ == '__main__':
   sys.exit(main())
