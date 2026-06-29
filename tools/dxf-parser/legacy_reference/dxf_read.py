import ezdxf
import sys

def write_to_file(f, e):
    f.write(
        "{} {}  {}   {}   {}\n".format(e.get_dxf_attrib('paperspace', 0), e.dxf.start[0], e.dxf.start[1], e.dxf.end[0],
                                       e.dxf.end[1]))

name, infile, outfile = sys.argv

seg_file = open("{}".format(outfile), "w")

doc = ezdxf.readfile("{}".format(infile))

# iterar sobre todas las entidades en modelspace
msp = doc.modelspace()

# query sobre todas las entidades del namespace que sean LINE
for e in msp.query('LINE'):
    write_to_file(seg_file, e)

seg_file.close()
