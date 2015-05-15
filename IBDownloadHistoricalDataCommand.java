package IBDownloadHistoricalData;

import com.ib.client.AnyWrapperMsgGenerator;
import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.EWrapperMsgGenerator;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;
import com.ib.client.Util;
import com.ib.client.ComboLeg;
import java.util.Vector;
import java.io.*;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.lang.Thread;
import java.util.List;

// To process arguments
import static org.kohsuke.args4j.ExampleMode.ALL;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;

/*
	Historical Data Limitations: https://www.interactivebrokers.com/en/software/api/apiguide/tables/historical_data_limitations.htm
	List of IB currencies: https://www.interactivebrokers.com/en/?f=%2Fen%2Ftrading%2Fexchanges.php%3Fexch%3Dibfxpro%26amp%3Bshowcategories%3D%26amp%3Bib_entity%3Dllc#
	Sample code to request data: http://www.elitetrader.com/et/index.php?threads/request-ib-tick-data-java-api.206254/
	Sample code: http://stackoverflow.com/questions/10777885/error-getting-the-eur-usd-historical-data-using-r-on-ibrokers
	
	Symbol: USD
	Security Type: CASH
	Exchange: IDEALPRO
	Primary Exchange: IDEALPRO
	Currency: JPY
	End Date/Time: 20150326 07:46:46 GMT
	Duration: 1 D, another eg could be 14400 S
	Bar Size Setting: 1 min
	What to Show: BID/ASK
	Regular Trading Hours: 1
	Date Format Style: 1
	
	Data files are generated in desired path in reverse chronological order eg
	1.txt => most recent data
	2.txt => next most recent data
	... etc
	
	
*/
// Example on how to run it: 
// java -jar IBDownloadHistoricalDataCommandLine.jar -currency=USD -exchange=SMART -pexchange=NASDAQ -rangenumber=1 -rangeunits=Y -security=STK -symbol=AAPL
class IBDownloadHistoricalDataCommand  implements EWrapper {

    private EClientSocket   mClient = new EClientSocket( this);
   
    // Create 2 logfiles
    private FileLog     mServerResponsesLog = new FileLog("ServerResponses.txt");
    private FileLog     mServerErrorsLog = new FileLog("ServerErrors.txt");
    
    private int mConnectionId=0;
    
    
    //CHANGE this between BID and ASK to get different fields
    private String mRequestField = "BID_ASK";
    private String mTicker="";
    
    //CHANGE this to tweak how long to wait for data to come in
    private boolean mIsThisRequestFinished = false;
    
    
    //Define Arguments
     @Option(name="-symbol",usage="Short code for the symbol: USD, AAPL, AMZN, ... ")
    private String mSymbol="USD";
     @Option(name="-rangenumber",usage="Number of units of 'rangeuntis' in to be requested (integer). ")
    private String mRangeNumber = "1";
    @Option(name="-rangeunits",usage="Time unit, to be used together with 'rangenumber' defines the range of data to be requested, S Seconds, D days, W weeks, M months, Y years")
    private String mRangeUnits="M";
    @Option(name="-currency",usage="Currency of the symbol: JPY, USD, EUR, AUD... ")
    private String mCurrency="JPY";
    @Option(name="-security",usage="Security for the symbol: STK, CASH, BOND, FUND, OPT, FUT, IND & more ... ")
    private String mSecurity="CASH";
    @Option(name="-exchange",usage="Exchange market for the symbol: IDEALPRO, SMART, & more ... ")
    private String mExchange="SMART";
    @Option(name="-pexchange",usage="Primary exchange market for the symbol: IDEALPRO, SMART, & more ... ")
    private String mPrimaryExchange="IDEALPRO";
    @Option(name="-field",usage="Field to ask for: BID, ASK, BID_ASK, TRADES, HISTORICAL_VOLATILTY ... ")
    private String mField="BID_ASK";
    @Option(name="-lastdate",usage="End date of the range to be requested Example/Default: 20150312")
    private String mLastDate="20150312";
    @Option(name="-lasthour",usage="End hour of the range to be requested Example/Default: 00:00:00")
    private String mLastHour="00:00:00";
   
     
    
    
    
    // --------------- MAIN ----------------------------------
    public static void main (String args[]) {
        //System.out.println("Starting IBDownloadHistoricalData");
        // Create a instance of this object.
        IBDownloadHistoricalDataCommand downloader = new IBDownloadHistoricalDataCommand();
        downloader.run(args);
    }
    
    private  void parseParameters (String args[]) {
        CmdLineParser parser = new CmdLineParser(this);
        
        // if you have a wider console, you could increase the value;
        // here 80 is also the default
        //parser.setUsageWidth(80);

        try {
            // parse the arguments.
            parser.parseArgument(args);

        } catch( CmdLineException e ) {
            // if there's a problem in the command line,
            // you'll get this exception. this will report
            // an error message.
            System.err.println(e.getMessage());
            System.err.println("java SampleMain [options...] arguments...");
            // print the list of available options
            parser.printUsage(System.err);
            System.err.println();
            // print option sample. This is useful some time
            System.err.println("  Example: java SampleMain"+parser.printExample(ALL));
            System.exit(3);
            
        }
        
    }
    
    // ------------ RUN METHOD ----------------------------
    void run(String args[]) {
        
        parseParameters(args);
     
       
        connect();
       
        Contract contract= new Contract();
        contract.m_conId=mConnectionId;  
        contract.m_symbol=mSymbol;  // Strange: This is the currency xD
        contract.m_secType=mSecurity; 
        contract.m_expiry="";
        contract.m_strike=0;
        contract.m_right="";
        contract.m_multiplier="";
        contract.m_exchange=mExchange;
        contract.m_currency=mCurrency; 
        contract.m_localSymbol="";
        contract.m_tradingClass="";
        contract.m_comboLegs=new Vector<ComboLeg>();
        contract.m_primaryExch=mPrimaryExchange;
        contract.m_includeExpired=false;
        contract.m_secIdType="";
        contract.m_secId="";
        
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd hh:mm:ss");
        String requestDateTimeStr = formatter.format(getLatestDownloadDate());
        
        mIsThisRequestFinished = false;
        mClient.reqHistoricalData( 0, contract, mLastDate+" "+mLastHour, mRangeNumber+" "+mRangeUnits, "1 day", mField, 1, 1, null);	
        //loop & wait for response from server
        int waitResponseCounter=0;
        while (!mIsThisRequestFinished) {
            try {
                waitResponseCounter++;
                Thread.sleep(500);
                
            } catch (Exception e) {
                   System.err.println(e);
                   System.exit(2);
              }
            if (waitResponseCounter>20){
                System.err.println("We haven't received any response from server. ");
                System.exit(1);
            }
        }
        disconnect();
        mServerResponsesLog.close();
        mServerErrorsLog.close();
        System.exit(0);
        
    }

    private Date getLatestDownloadDate() {
        //the day before at 12midnight
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);   
        cal.add(Calendar.DATE, -1);
        Date date1DayBefore = cal.getTime();
        return date1DayBefore;
    }

    //CHANGE this to the first download date, which depends on how much data access you have in your IB account
    private Date getFirstDownloadDate() {
            Calendar cal = Calendar.getInstance();
            cal.set(2007, 6, 1, 0, 0);
            Date d = cal.getTime();
            return d;
    }


	
    void connect() {
        int contador=0;
        //connect localhost port 7496
        mClient.eConnect("", 7496, mConnectionId); 
        while (!mClient.isConnected()) {
            try {
                contador++;
                Thread.sleep(500);
                
            } catch (Exception e) {
                            System.err.println(e);
              }
            if (contador>10) {
                mClient.eDisconnect();
                System.err.println("We cannot connect to the TWS.");
                System.exit(1);
            }
        } 
        if (mClient.isConnected()) {
            mServerResponsesLog.add("Connected to Tws server version " +
                       mClient.serverVersion() + " at " +
                       mClient.TwsConnectionTime());
        }
    }

    void disconnect() {
        mClient.eDisconnect();
    }

    
	
    public void nextValidId( int orderId) {
        // received next valid order id
    	String msg = EWrapperMsgGenerator.nextValidId( orderId);
        mServerResponsesLog.add(msg) ;
	mServerResponsesLog.flush();
    }

    public void error( String str) {
    	String msg = AnyWrapperMsgGenerator.error(str);
        mServerErrorsLog.add( msg);
		mServerErrorsLog.flush();
    }

    public void error( int id, int errorCode, String errorMsg) {
    	String msg = AnyWrapperMsgGenerator.error(id, errorCode, errorMsg);
        mServerErrorsLog.add( msg);
		mServerErrorsLog.flush();
    }

    public void connectionClosed() {
        String msg = AnyWrapperMsgGenerator.connectionClosed();
    }

    // Is called once per data line returned
    public void historicalData(int reqId, String date, double open, double high, double low,
                               double close, int volume, int count, double WAP, boolean hasGaps) {
        
        //String msg = EWrapperMsgGenerator.historicalData(reqId, date, open, high, low, close, volume, count, WAP, hasGaps);
        
        String msg= ""+date+";"+close;
        
        if (msg.toUpperCase().contains("FINISHED")) {
            mIsThisRequestFinished = true;
	}
	else {                
            System.out.println(msg);
	}
    }
	
	

    // ****************************************************************************************
    // *                            FILE LOG                                                  *
    // ****************************************************************************************
    class FileLog {
            PrintWriter writer = null;
            public String mFilePath;

            public FileLog(String filePath) {
                    mFilePath = filePath;
                    try {
                            writer = new PrintWriter(new BufferedWriter(new FileWriter(filePath, true)));
                    }catch (Exception e) {
                            System.err.println(e);
                    }
            }

            public void add(String msg) {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd hh:mm:ss");
                    String nowDateTimeStr = formatter.format(new Date());
                    writer.write(nowDateTimeStr + " " + msg + "\n");
            }

            public void close() {
                    writer.flush();
                    writer.close();
            }

            public void flush() {
                    writer.flush();
            }

            public void delete() {
                    try {
                            File file = new File(mFilePath);
                            file.delete();
                    } catch (Exception e) {
                            System.err.println("Failed to delete file: " + mFilePath);
                    }

            }
    }
    // ************************************************************************************    
    // *                           INTERFACE METHODS WE ARE NOT USING                     *
    // ************************************************************************************
    public void displayGroupUpdated( int reqId, String contractInfo){}
    public void displayGroupList( int reqId, String groups){}
    public void verifyCompleted( boolean isSuccessful, String errorText){}
    public void verifyMessageAPI( String apiData){}
    public void tickPrice( int tickerId, int field, double price, int canAutoExecute) {}
    public void tickOptionComputation( int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend,double gamma, double vega, double theta, double undPrice) {}
    public void tickSize( int tickerId, int field, int size) {}
    public void tickGeneric( int tickerId, int tickType, double value) {}
    public void tickString( int tickerId, int tickType, String value) {}
    public void tickSnapshotEnd(int tickerId) {}
    public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints,double impliedFuture, int holdDays, String futureExpiry, double dividendImpact,double dividendsToExpiry) {}
    public void orderStatus( int orderId, String status, int filled, int remaining,double avgFillPrice, int permId, int parentId,double lastFillPrice, int clientId, String whyHeld) {}
    public void openOrder( int orderId, Contract contract, Order order, OrderState orderState) {}
    public void openOrderEnd(){}
    public void contractDetails(int reqId, ContractDetails contractDetails) {}
    public void contractDetailsEnd(int reqId) {}
    public void scannerData(int reqId, int rank, ContractDetails contractDetails,String distance, String benchmark, String projection, String legsStr) {}
    public void scannerDataEnd(int reqId) {}
    public void bondContractDetails(int reqId, ContractDetails contractDetails){}
    public void execDetails(int reqId, Contract contract, Execution execution){}
    public void execDetailsEnd(int reqId){}
    public void updateMktDepth( int tickerId, int position, int operation,int side, double price, int size) {}
    public void updateMktDepthL2( int tickerId, int position, String marketMaker,int operation, int side, double price, int size) {}
    public void updateAccountValue(String key, String value,String currency, String accountName) {}
    public void updatePortfolio(Contract contract, int position, double marketPrice,double marketValue, double averageCost, double unrealizedPNL, double realizedPNL,String accountName) {}
    public void updateAccountTime(String timeStamp) {}
    public void accountDownloadEnd(String accountName) {}
    public void updateNewsBulletin( int msgId, int msgType, String message, String origExchange) {}
    public void managedAccounts( String accountsList) {}
    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {}	
    public void scannerParameters(String xml) {}
    public void currentTime(long time) {}
    public void fundamentalData(int reqId, String data) {}
    public void deltaNeutralValidation(int reqId, UnderComp underComp) {}
    public void receiveFA(int faDataType, String xml) {}
    public void marketDataType(int reqId, int marketDataType) {}
    public void commissionReport(CommissionReport commissionReport) {}
    public void position(String account, Contract contract, int pos, double avgCost) {}
    public void positionEnd() {}
    public void accountSummary( int reqId, String account, String tag, String value, String currency) {}
    public void accountSummaryEnd( int reqId) {}
    public void error(Exception ex) {}
        
}
