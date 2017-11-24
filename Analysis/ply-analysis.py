import argparse
import sys
import os
import io

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

try:
   from plyfile import PlyData, PlyElement
except:
   print('Install python-plyfile from https://github.com/dranjan/python-plyfile (using pip: pip install plyfile')
   sys.exit(1)

def main(argv=None):
   if argv is None:
      argv = sys.argv
   parser = argparse.ArgumentParser(description='Analyze points from .ply files')
   parser.add_argument("-s", '--stats', dest='stats',action="store_true", default=False,
   help="Print stats")
   parser.add_argument("-d", '--distributions', dest='distributions',action="store_true", default=False,
   help="Show probability distributions for x, y and z.")
   parser.add_argument('--Jxy', dest='jointxy',action="store_true", default=False,
   help="Show joint probability distribution for x and y.")
   parser.add_argument('--Jxz', dest='jointxz',action="store_true", default=False,
   help="Show joint probability distribution for x and z.")
   parser.add_argument('--Jyz', dest='jointyz',action="store_true", default=False,
   help="Show joint probability distribution for y and z.")
   parser.add_argument("-b", '--box', dest='box',action="store_true", default=False,
   help="Show boxplot.")
   parser.add_argument('ply', metavar='{file.ply}', nargs='?', default='-',
                       help='The .ply file. Empty or - to process standard input')
   args = parser.parse_args()
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
   vertices = pd.DataFrame(plydata['vertex'].data)
   fig = 1
   show_plot = False
   if args.stats:
      print(vertices.describe())
   if args.distributions:
      plt.figure(fig)
      sns.kdeplot(vertices.x)
      fig = fig + 1
      plt.figure(fig)
      sns.kdeplot(vertices.y)
      fig = fig + 1
      plt.figure(fig)
      sns.kdeplot(vertices.z)
      fig = fig + 1
      show_plot = True
   if args.box:
      plt.figure(fig)
      vertices.boxplot()
      fig = fig + 1
      show_plot = True
   if args.jointxy:
      plt.figure(fig)
      sns.jointplot(vertices.x, vertices.y, kind="kde", size=7, space=0)
      fig = fig + 1
      show_plot = True
   if args.jointxz:
      plt.figure(fig)
      sns.jointplot(vertices.x, vertices.z, kind="kde", size=7, space=0)
      fig = fig + 1
      show_plot = True
   if args.jointyz:
      plt.figure(fig)
      sns.jointplot(vertices.y, vertices.z, kind="kde", size=7, space=0)
      fig = fig + 1
      show_plot = True
   if show_plot:
      plt.show()

if __name__ == '__main__':
   sys.exit(main())
