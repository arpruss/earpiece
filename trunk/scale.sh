cd res/drawable-hdpi
for x in *.png ; do 
  convert $x -scale 66.6666666666666% ../drawable-mdpi/$x
  convert $x -scale 50% ../drawable-ldpi/$x
done
