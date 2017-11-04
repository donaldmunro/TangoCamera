#inkscape -f $1.svg --export-area=68:68:417:417 -e $1.png
inkscape -f $1.svg -w 72 -h 72 -e res/drawable-hdpi/$1.png
inkscape -f $1.svg -w 128 -h 128 -e res/drawable-mdpi/$1.png
inkscape -f $1.svg -w 256 -h 256 -e res/drawable-xhdpi/$1.png
