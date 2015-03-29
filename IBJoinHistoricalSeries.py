import numpy as np
import numpy.lib.recfunctions as rfn
import pandas as pd

#The logic in this file takes many the data files generated by IBCombineHistoricalData
#"joins" them by datetime to create a file with one date-time column and numerous data columns
if __name__ == "__main__":
    #MANUALLY SET THESE FILES FOR JOINING
    filesToJoin = [
        "C:/Dropbox/CninSrc/JTS/TWS API/samples/Java/Data/CAD_BID.txt",
        "C:/Dropbox/CninSrc/JTS/TWS API/samples/Java/Data/CAD_ASK.txt"
    ]
    #MANUALLY SET THIS FOR OUTPUT FILE
    outFile = "C:/Dropbox/CninSrc/JTS/TWS API/samples/Java/Data/JOINED.txt"
    #MANUALLY SET THIS HEADER SO EASIER TO REMEMBER COLUMNS
    headerTxt = "Time,CAD_BID,CAD_ASK"

    joined = []
    for file in filesToJoin:
        print "Handing: " + file
        data = np.genfromtxt(file, delimiter = ',', dtype=np.dtype([('time',np.long), ('price', np.float)]))
        print "Joining"
        if len(joined) == 0:
            joined = data
        else:
            joined = rfn.join_by('time', joined, data, jointype='inner', usemask=False)

    np.savetxt(outFile, joined, delimiter=',', fmt="%s", header=headerTxt, comments="")
