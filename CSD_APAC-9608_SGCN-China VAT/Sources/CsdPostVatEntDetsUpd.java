package com.temenos.csd.vat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.temenos.api.TField;
import com.temenos.api.TStructure;
import com.temenos.csd.vat.CsdWithStandTaxCalc.VatWhtResult;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.arrangement.accounting.Contract;
import com.temenos.t24.api.complex.aa.activityhook.ArrangementContext;
import com.temenos.t24.api.complex.aa.activityhook.TransactionData;
import com.temenos.t24.api.hook.arrangement.ActivityLifecycle;
import com.temenos.t24.api.records.aaaccountdetails.AaAccountDetailsRecord;
import com.temenos.t24.api.records.aaaccountdetails.BillIdClass;
import com.temenos.t24.api.records.aaaccountdetails.BillPayDateClass;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;
import com.temenos.t24.api.records.aabilldetails.AaBillDetailsRecord;
import com.temenos.t24.api.records.aabilldetails.PropertyClass;
import com.temenos.t24.api.records.aainterestaccruals.AaInterestAccrualsRecord;
import com.temenos.t24.api.records.aaprddesaccounting.AaPrdDesAccountingRecord;
import com.temenos.t24.api.records.aaprddesinterest.AaPrdDesInterestRecord;
import com.temenos.t24.api.records.aaprddestax.AaPrdDesTaxRecord;
import com.temenos.t24.api.records.aaproductcatalog.AaProductCatalogRecord;
import com.temenos.t24.api.records.account.AccountRecord;
import com.temenos.t24.api.records.acentryparam.AcEntryParamRecord;
import com.temenos.t24.api.records.currency.CurrencyMarketClass;
import com.temenos.t24.api.records.currency.CurrencyRecord;
import com.temenos.t24.api.records.customer.CustomerRecord;
import com.temenos.t24.api.records.ebcsdacctentdetails.EbCsdAcctEntDetailsRecord;
import com.temenos.t24.api.records.ebcsdacctentdetailseod.EbCsdAcctEntDetailsEodRecord;
import com.temenos.t24.api.records.ebcsdstoreplcategparam.EbCsdStorePlCategParamRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.ClientBrdIdClass;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.InterestDetailClass;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.PeriodStartClass;
import com.temenos.t24.api.records.ebcsdtaxdetsparam.EbCsdTaxDetsParamRecord;
import com.temenos.t24.api.records.ebcsdvatwhtbestfitupd.EbCsdVatWhtBestFitUpdRecord;
import com.temenos.t24.api.records.ebcsdvatwhtrateparam.EbCsdVatWhtRateParamRecord;
import com.temenos.t24.api.records.tax.TaxRecord;
import com.temenos.t24.api.records.taxtypecondition.TaxTypeConditionRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebcsdacctentdetails.EbCsdAcctEntDetailsTable;
import com.temenos.t24.api.tables.ebcsdacctentdetailseod.EbCsdAcctEntDetailsEodTable;
import com.temenos.t24.api.tables.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdTable;

/**
 * @author s.aananthalakshmi
 * AttachedTo : ACCOUNTS-MAKEDUE-SCHEDULE
 *              ACCOUNTS-CAPITALISE-SCHEDULE
 *              DEPOSITS-MAKEDUE-SCHEDULE
 *              DEPOSITS-MAKEDUE-SCHEDULE
 * AttachedAs : Post Routine in Activity API
 * Description : Routine to calculate the Input VAT for Credit Interest Amount
 *               and raising Input VAT+WHT accounting entries and updating live table
 * 
 * @update v.manoj - TSR-1151911 - China VAT Delivery: Issue with LCY Amount
 * @update Jayaraman - TSR-1162200 - Sub division code logic is updated
 */
public class CsdPostVatEntDetsUpd extends ActivityLifecycle {

    DataAccess da = new DataAccess();
    Session ySess = new Session();
    Contract yCon = new Contract();
    String vatCalcAmt = "";
    String creditIntAmt = "";
    String whtCalcAmt = "";
    String whtCalcType = "";
    String whtRateValue = "";
    String yPayIndication = "";
    String actEffectiveDte = "";
    boolean yBstFitFlag = false;
    boolean yIntTaxDetsFlg = false;
    int bestFitPos;
    boolean yFcyFlag = false;
    String yTaxType = "";
    String yPLType = "";
    String inputVatCalcType = "";
    String yCsmBulkNm = "CSMBULK";
    String makeDueActNm = "MAKEDUE";
    String capActNm = "CAPITALISE";
    String systemNm = "SYSTEM";
    String systemId = "AC";
    
    public static final String EXPENSE = "Expense";
    public static final String INCLUSIVE = "Inclusive";
    public static final String EXCLUSIVE = "Exclusive";
    
    private static Logger logger = LoggerFactory.getLogger("API");

    @Override
    public void postCoreTableUpdate(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord arrangementActivityRecord, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure record,
            List<TransactionData> transactionData, List<TStructure> transactionRecord) {

        if (!arrangementContext.getActivityStatus().equals("AUTH")) {
            return;
        }
        
        logger.debug(" ******* Account Cap Schedule Api Routine called ******* ");
        logger.debug("String ActivityId = "+ arrangementContext.getActivityId());
        String currActivity = arrangementActivityRecord.getActivity().getValue();
        logger.debug("String CurrentActivityId = "+ currActivity);
        String activityName = getActivityNm(currActivity);
        logger.debug("String activityName = "+ activityName);
        
        da = new DataAccess(this);
        ySess = new Session(this);
        yCon = new Contract(this);
        
        // Get the ArrangementId from the current Activity
        String arrangementId = arrangementActivityRecord.getArrangement().getValue();
        yCon.setContractId(arrangementId);
        logger.debug("String Arrangement Id = "+arrangementId);
        List<String> intPropertyList = yCon.getPropertyIdsForPropertyClass("INTEREST");
        logger.debug("String intPropertyList = "+ intPropertyList);
        String intProp = intPropertyList.get(0);
        logger.debug("String intProp = "+ intProp);
        AaPrdDesInterestRecord yPrdIntRec = new AaPrdDesInterestRecord(yCon.getConditionForProperty(intProp));
        String intPropName = yPrdIntRec.getIdComp2().getValue(); // Get the Interest Property Name
        logger.debug("String intPropName = "+ intPropName);
        // Get the calculated WHT amount and received Credit Interest
        getCalcTaxAmount(intPropName, arrangementContext, accountDetailRecord);

        if (!yIntTaxDetsFlg) {
            return;
        }
        logger.debug("Bill Details satisfied");
        // Get the Account Details
        String acctNumber = getAccountId(arrangementActivityRecord.getArrangement().getValue());
        AccountRecord yAcctRec = getAcctRecord(acctNumber);
        // Get the CustomerId
        String yCustomerId = yAcctRec.getCustomer().getValue();
        // Get the Customer Details
        CustomerRecord yCustomerRec = getCustomerRec(yCustomerId);
        // Get the Currency of Deposits/CASA
        String acctCurr = arrangementRecord.getCurrency().getValue();
        // Get the Local Currency
        String localCcy = ySess.getLocalCurrency();
        // FCY Credit Interest Flag
        yFcyFlag = getFcyTransFlag(acctCurr, localCcy);
        logger.debug("Fcy Entry = "+yFcyFlag);
        // Check the account record available in the Live table
        EbCsdTaxDetailsUpdRecord yTaxDetsRec = checkLiveTableRec(acctNumber, yAcctRec, yCustomerRec, yCustomerId);
        logger.debug("String yTaxDetsRec = "+yTaxDetsRec);
        // Get the WHT Rate Configured from the EB.CSD.VAT.WHT.RATE.PARAM
        String taxRateVal = "";
        EbCsdVatWhtRateParamRecord yVatWhtParamRec = new EbCsdVatWhtRateParamRecord(this);

        // get the PLCategory from the
        String plCategory = getPlCategFromAccounting(intPropName);
        // Check the PL Category is parameterized or not.
        String yPlCategParamRecId = checkPLCategParam(plCategory);
        logger.debug("String yPlCategParamRecId = "+yPlCategParamRecId);
        if (!yPlCategParamRecId.isBlank()) {
            // Get the Parameterized record for the respective PL Category
            yVatWhtParamRec = getVatWhtParamRecord(yPlCategParamRecId);
            String prodCateg = yAcctRec.getCategory().getValue();
            bestFitPos = checkBestFixPos(prodCateg, yCustomerRec, yPlCategParamRecId);
            taxRateVal = getWhtRateParamVal(yVatWhtParamRec, bestFitPos);
        }
        
        logger.debug("Cust = "+yCustomerId);
        logger.debug("Best fit = "+bestFitPos);
        vatCalcAmt = calculateInputVat(creditIntAmt, taxRateVal);
        // Get the exchange rate for the FCY Credit Interest
        String yVatExcRate = calExhangeRate(acctCurr, yFcyFlag, "VAT");
        logger.debug("String yVatExcRate = "+yVatExcRate);
        logger.debug("Vatamt = "+vatCalcAmt);
        // Update Interest Details
        updVatInterestDets(yTaxDetsRec, yAcctRec, taxRateVal, yFcyFlag, yVatExcRate, intPropName);
        logger.debug("String yTaxDetsRec = "+yTaxDetsRec);
        // Write the Transaction details into Live Table
        writeDetstoLiveTable(yTaxDetsRec, acctNumber);
        
        if (activityName.equalsIgnoreCase(capActNm)) {
            logger.debug(" leg1 capitalize condition satisfied ");
            // Raising Leg 2 Entries into EB.CSD.ACCT.ENT.DETAILS.EOD
            updVatLeg1LcyEntDetsEod(yVatWhtParamRec, yAcctRec, acctNumber, arrangementContext, localCcy, plCategory, arrangementActivityRecord, yVatExcRate);
        } else if (activityName.equalsIgnoreCase(makeDueActNm)) {
            logger.debug(" leg1 makedue condition satisfied ");
            // Raising Leg 2 Entries into EB.CSD.ACCT.ENT.DETAILS
            updVatLeg1EntriesDets(yVatWhtParamRec, yAcctRec, acctNumber, arrangementContext, localCcy, plCategory);
        }
        
        if (yFcyFlag) {
            if (activityName.equalsIgnoreCase(capActNm)) {
                logger.debug(" leg2 capitalize condition satisfied ");
                updVatLeg2EntFcyDetsEod(yVatWhtParamRec, yAcctRec, acctNumber, arrangementContext, localCcy, plCategory, arrangementActivityRecord, yVatExcRate);
            } else if (activityName.equalsIgnoreCase(makeDueActNm)) {
                logger.debug(" leg2 makedue condition satisfied ");
                updVatLeg2EntriesDets(yVatWhtParamRec, yAcctRec, acctNumber, yVatExcRate, arrangementContext, localCcy);
            }
            
            boolean credBillPymtInd = false;
            if (yPayIndication.equalsIgnoreCase("Credit")) {
                credBillPymtInd = true;
            }
            // Raising WHT FCY Accounting Entries when WHT Configured
            if (!whtCalcAmt.isBlank() && !whtCalcType.isBlank()) {
                // Get the exchange rate for the FCY Credit Interest
                String yWhtExcRate = calExhangeRate(acctCurr, yFcyFlag, "WHT");
                logger.debug("String yWhtExcRate = "+yWhtExcRate);
                logger.debug(" **** WHT Calc Type + FCY Configured in parametrization **** ");
                if (activityName.equalsIgnoreCase(capActNm)) {
                    logger.debug(" leg2 capitalize condition satisfied ");
                    // Raising Leg 2 Entries into EB.CSD.ACCT.ENT.DETAILS.EOD
                    updWhtLeg2FcyEntDetsEod(yVatWhtParamRec, yAcctRec, acctNumber, yWhtExcRate, arrangementContext, arrangementActivityRecord, credBillPymtInd);
                } else if (activityName.equalsIgnoreCase(makeDueActNm)) {
                    logger.debug(" leg2 makedue condition satisfied ");
                    // Raising Leg 2 Entries into EB.CSD.ACCT.ENT.DETAILS
                    updWhtFcyEntDets(yVatWhtParamRec, yAcctRec, acctNumber, yWhtExcRate, arrangementContext);
                } 
            }
        }
        
    }

    //
    public void updWhtLeg2FcyEntDetsEod(EbCsdVatWhtRateParamRecord yVatWhtParamRec, AccountRecord yAcctRec,
            String acctNumber, String yWhtExcRate, ArrangementContext arrangementContext, AaArrangementActivityRecord arrangementActivityRecord, boolean credBillPymtInd) {
        /** String lastWorkingDay = ySess.getCurrentVariable("!LAST.WORKING.DAY"); */
        String todayDay = ySess.getCurrentVariable("!TODAY");
        // get AC.ENTRY.PARAM record
        AcEntryParamRecord acEntParamRec = getAcParamRec("WHT");
        String yDelimit1 = "*";
        // Set the debit Leg
        String ydbtLcyL2Acct = "";
        String ydbtLcyL2CompanyCde = "";
        String ydbtLcyL2AmtLcy = "";
        String ydbtLcyL2TransCode = "";
        String ydbtLcyL2PlCateg = "";
        String ydbtLcyL2CustId = "";
        String ydbtLcyL2AcctOff = "";
        String ydbtLcyL2ProdCateg = "";
        String ydbtLeg2ValueDate = "";
        String ydbtLcyL2Ccy = "";
        String ydbtLcyL2AmtFcy = "";
        String ydbtLcyL2ExchgRte = "";
        String ydbtLcyL2PosType = "";
        String ydbtLcyL2OutRef = "";
        String ydbtLcyL2ExpoDate = "";
        String ydbtLcyL2CcyMkt = "";
        String ydbtLcyL2TransRef = "";
        String ydbtLcyL2SysId = "";
        String ydbtLcyL2BookingDte = "";
        
        ydbtLcyL2Acct = getvatPayAcct(yVatWhtParamRec.getWhtPayableAccount().getValue(), yAcctRec.getCurrency().getValue());
        AccountRecord yDebAcctRecObj = getLegAcctRec(ydbtLcyL2PlCateg, ydbtLcyL2Acct, yAcctRec);
        ydbtLcyL2CompanyCde = arrangementActivityRecord.getCoCode(); 
        ydbtLcyL2AmtLcy = getWhtExchgCcyAmt(yWhtExcRate);
        /** ydbtLcyL2TransCode = acEntParamRec.getDrTxnCode().getValue(); */
        ydbtLcyL2TransCode = fetchTransCode(acEntParamRec, credBillPymtInd, "D");
        ydbtLcyL2CustId = yAcctRec.getCustomer().getValue();
        ydbtLcyL2AcctOff = yDebAcctRecObj.getAccountOfficer().getValue();
        ydbtLcyL2ProdCateg = yDebAcctRecObj.getCategory().getValue();
        ydbtLeg2ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        ydbtLcyL2Ccy = yAcctRec.getCurrency().getValue();
        ydbtLcyL2AmtFcy = whtCalcAmt;
        ydbtLcyL2ExchgRte = yWhtExcRate;
        ydbtLcyL2PosType = yDebAcctRecObj.getPositionType().getValue();
        ydbtLcyL2OutRef = acctNumber;
        ydbtLcyL2ExpoDate = todayDay;
        ydbtLcyL2CcyMkt = yDebAcctRecObj.getCurrencyMarket().getValue();
        ydbtLcyL2TransRef = arrangementContext.getTransactionReference();
        ydbtLcyL2SysId = systemId;
        ydbtLcyL2BookingDte = todayDay;

        // Forming DebitLeg Message
        String debitLcyL2Entries = ydbtLcyL2Acct.concat(yDelimit1).concat(ydbtLcyL2CompanyCde).concat(yDelimit1)
                .concat(ydbtLcyL2AmtLcy).concat(yDelimit1).concat(ydbtLcyL2TransCode).concat(yDelimit1).concat(ydbtLcyL2PlCateg)
                .concat(yDelimit1).concat(ydbtLcyL2CustId).concat(yDelimit1).concat(ydbtLcyL2AcctOff).concat(yDelimit1)
                .concat(ydbtLcyL2ProdCateg).concat(yDelimit1).concat(ydbtLeg2ValueDate).concat(yDelimit1).concat(ydbtLcyL2Ccy)
                .concat(yDelimit1).concat(ydbtLcyL2AmtFcy).concat(yDelimit1).concat(ydbtLcyL2ExchgRte).concat(yDelimit1)
                .concat(ydbtLcyL2PosType).concat(yDelimit1).concat(ydbtLcyL2OutRef).concat(yDelimit1).concat(ydbtLcyL2ExpoDate)
                .concat(yDelimit1).concat(ydbtLcyL2CcyMkt).concat(yDelimit1).concat(ydbtLcyL2TransRef).concat(yDelimit1)
                .concat(ydbtLcyL2SysId).concat(yDelimit1).concat(ydbtLcyL2BookingDte).concat(yDelimit1).concat("").concat(yDelimit1)
                .concat("CSD."+capActNm);
        logger.debug("String Stmt debitLcyL2Entries = "+ debitLcyL2Entries);
        
        // Set the CreditLeg for the AcctEntries1
        String ycdtLcyL2Acct = "";
        String ycdtLcyL2CompanyCode = "";
        String ycdtLcyL2AmtLcy = "";
        String ycdtLcyL2TransCode = "";
        String ycdtLcyL2PlCateg = "";
        String ycdtLcyL2CustId = "";
        String ycdtLcyL2AcctOff = "";
        String ycdtLcyL2ProdCateg = "";
        String ycdtLcyL2ValueDate = "";
        String ycdtLcyL2Ccy = "";
        String ycdtLcyL2AmtFcy = "";
        String ycdtLcyL2ExchgRte = "";
        String ycdtLcyL2PosType = "";
        String ycdtLcyL2OutRef = "";
        String ycdtLcyL2ExpoDate = "";
        String ycdtLcyL2CcyMkt = "";
        String ycdtLcyL2TransRef = "";
        String ycdtLcyL2SysId = "";
        String ycdtLcyL2BookingDte = "";
        
        ycdtLcyL2Acct = getLcyInternalAcct(ydbtLcyL2Acct);
        // Get the Account Record.
        AccountRecord yCdtAcctRecObj = getLegAcctRec(ycdtLcyL2PlCateg, ycdtLcyL2Acct, yAcctRec);
        ycdtLcyL2CompanyCode = arrangementActivityRecord.getCoCode();
        ycdtLcyL2AmtLcy = ydbtLcyL2AmtLcy;
        /** ycdtLcyL2TransCode = acEntParamRec.getCrTxnCode().getValue(); */
        ycdtLcyL2TransCode = fetchTransCode(acEntParamRec, credBillPymtInd, "C");
        ycdtLcyL2CustId = yAcctRec.getCustomer().getValue();
        ycdtLcyL2AcctOff = yCdtAcctRecObj.getAccountOfficer().getValue();
        ycdtLcyL2ProdCateg = yCdtAcctRecObj.getCategory().getValue();
        ycdtLcyL2ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        ycdtLcyL2Ccy = ySess.getLocalCurrency();
        ycdtLcyL2PosType = yCdtAcctRecObj.getPositionType().getValue();
        ycdtLcyL2OutRef = acctNumber;
        ycdtLcyL2ExpoDate = todayDay;
        ycdtLcyL2CcyMkt = yCdtAcctRecObj.getCurrencyMarket().getValue();
        ycdtLcyL2TransRef = arrangementContext.getTransactionReference();
        ycdtLcyL2SysId = systemId;
        ycdtLcyL2BookingDte = todayDay;
        
        // Forming CreditLeg Message
        String creditLcyL2Entries = ycdtLcyL2Acct.concat(yDelimit1).concat(ycdtLcyL2CompanyCode).concat(yDelimit1)
                .concat(ycdtLcyL2AmtLcy).concat(yDelimit1).concat(ycdtLcyL2TransCode).concat(yDelimit1).concat(ycdtLcyL2PlCateg)
                .concat(yDelimit1).concat(ycdtLcyL2CustId).concat(yDelimit1).concat(ycdtLcyL2AcctOff).concat(yDelimit1)
                .concat(ycdtLcyL2ProdCateg).concat(yDelimit1).concat(ycdtLcyL2ValueDate).concat(yDelimit1).concat(ycdtLcyL2Ccy)
                .concat(yDelimit1).concat(ycdtLcyL2AmtFcy).concat(yDelimit1).concat(ycdtLcyL2ExchgRte).concat(yDelimit1)
                .concat(ycdtLcyL2PosType).concat(yDelimit1).concat(ycdtLcyL2OutRef).concat(yDelimit1).concat(ycdtLcyL2ExpoDate)
                .concat(yDelimit1).concat(ycdtLcyL2CcyMkt).concat(yDelimit1).concat(ycdtLcyL2TransRef).concat(yDelimit1)
                .concat(ycdtLcyL2SysId).concat(yDelimit1).concat(ycdtLcyL2BookingDte).concat(yDelimit1).concat("").concat(yDelimit1)
                .concat("CSD."+capActNm);
        logger.debug("String Stmt creditLcyL2Entries = "+ creditLcyL2Entries);
        
        String finalAcctEnt = "";
        if (yPayIndication.equalsIgnoreCase("Debit")) {
            finalAcctEnt = creditLcyL2Entries.concat("$$").concat(debitLcyL2Entries);
        } else {
            finalAcctEnt = debitLcyL2Entries.concat("$$").concat(creditLcyL2Entries);
        }
        logger.debug("String WHT finalAcctEnt = "+ finalAcctEnt);
        
        EbCsdAcctEntDetailsEodRecord acctEntDetsEodVatleg2Rec = new EbCsdAcctEntDetailsEodRecord(this);
        acctEntDetsEodVatleg2Rec.setAccountingEntry(finalAcctEnt);
        logger.debug("String acctEntDetsEodVatleg1Rec = "+ acctEntDetsEodVatleg2Rec);
        // Forming Unique Reference Number followed with account number
        String uniqueIdVatLeg2 = UUID.randomUUID().toString();
        logger.debug("String uniqueIdVatLeg2 = "+ uniqueIdVatLeg2);
        // Write the details into EB.CSD.ACCT.ENT.DETAILS.EOD
        writeAcctEntEodDets(uniqueIdVatLeg2, acctEntDetsEodVatleg2Rec);
        
    }

    //
    private String fetchTransCode(AcEntryParamRecord acEntParamRec, boolean credBillPymtInd, String tranSide) {
        String transCode = "";
        if (tranSide.equalsIgnoreCase("D") && credBillPymtInd) {
            transCode = acEntParamRec.getDrTxnCode().getValue();
        } else if (tranSide.equalsIgnoreCase("C") && credBillPymtInd) {
            transCode = acEntParamRec.getCrTxnCode().getValue();
        } else if (tranSide.equalsIgnoreCase("D") && !credBillPymtInd) {
            transCode = acEntParamRec.getCrTxnCode().getValue();
        } else if (tranSide.equalsIgnoreCase("C") && !credBillPymtInd){
            transCode = acEntParamRec.getDrTxnCode().getValue();
        }
        return transCode;
    }

    //
    public void updVatLeg2EntFcyDetsEod(EbCsdVatWhtRateParamRecord yVatWhtParamRec, AccountRecord yAcctRec,
            String acctNumber, ArrangementContext arrangementContext, String localCcy, String plCategory,
            AaArrangementActivityRecord arrangementActivityRecord, String yVatExcRate) {
        /** String lastWorkingDay = ySess.getCurrentVariable("!LAST.WORKING.DAY"); */
        String todayDay = ySess.getCurrentVariable("!TODAY");
        logger.debug("String Input VAT Leg2 todayDay = "+ todayDay);
        // get AC.ENTRY.PARAM record
        AcEntryParamRecord acEntParamRec = getAcParamRec("VAT");
        String yDelimit1 = "*";
        // Set the debit Leg
        String dbtLcyL2Acct = "";
        String dbtLcyL2CompanyCde = "";
        String dbtLcyL2AmtLcy = "";
        String dbtLcyL2TransCode = "";
        String dbtLcyL2PlCateg = "";
        String dbtLcyL2CustId = "";
        String dbtLcyL2AcctOff = "";
        String dbtLcyL2ProdCateg = "";
        String dbtLeg2ValueDate = "";
        String dbtLcyL2Ccy = "";
        String dbtLcyL2AmtFcy = "";
        String dbtLcyL2ExchgRte = "";
        String dbtLcyL2PosType = "";
        String dbtLcyL2OutRef = "";
        String dbtLcyL2ExpoDate = "";
        String dbtLcyL2CcyMkt = "";
        String dbtLcyL2TransRef = "";
        String dbtLcyL2SysId = "";
        String dbtLcyL2BookingDte = "";
        
        dbtLcyL2Acct = getvatPayAcct(yVatWhtParamRec.getVatPayableAccount().getValue(), yAcctRec.getCurrency().getValue());
        AccountRecord yDebAcctRecObj = getLegAcctRec(dbtLcyL2PlCateg, dbtLcyL2Acct, yAcctRec);
        dbtLcyL2CompanyCde = arrangementActivityRecord.getCoCode(); 
        dbtLcyL2AmtLcy = getExchCcyAmt(yVatExcRate);
        dbtLcyL2TransCode = acEntParamRec.getDrTxnCode().getValue();
        dbtLcyL2CustId = yAcctRec.getCustomer().getValue();
        dbtLcyL2AcctOff = yDebAcctRecObj.getAccountOfficer().getValue();
        dbtLcyL2ProdCateg = yDebAcctRecObj.getCategory().getValue();
        dbtLeg2ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        dbtLcyL2Ccy = yAcctRec.getCurrency().getValue();
        dbtLcyL2AmtFcy = vatCalcAmt;
        dbtLcyL2ExchgRte = yVatExcRate;
        dbtLcyL2PosType = yDebAcctRecObj.getPositionType().getValue();
        dbtLcyL2OutRef = acctNumber;
        dbtLcyL2ExpoDate = todayDay;
        dbtLcyL2CcyMkt = yDebAcctRecObj.getCurrencyMarket().getValue();
        dbtLcyL2TransRef = arrangementContext.getTransactionReference();
        dbtLcyL2SysId = systemId;
        dbtLcyL2BookingDte = todayDay;
        
        // Forming DebitLeg Message
        String debitLcyL2Entries = dbtLcyL2Acct.concat(yDelimit1).concat(dbtLcyL2CompanyCde).concat(yDelimit1)
                .concat(dbtLcyL2AmtLcy).concat(yDelimit1).concat(dbtLcyL2TransCode).concat(yDelimit1).concat(dbtLcyL2PlCateg)
                .concat(yDelimit1).concat(dbtLcyL2CustId).concat(yDelimit1).concat(dbtLcyL2AcctOff).concat(yDelimit1)
                .concat(dbtLcyL2ProdCateg).concat(yDelimit1).concat(dbtLeg2ValueDate).concat(yDelimit1).concat(dbtLcyL2Ccy)
                .concat(yDelimit1).concat(dbtLcyL2AmtFcy).concat(yDelimit1).concat(dbtLcyL2ExchgRte).concat(yDelimit1)
                .concat(dbtLcyL2PosType).concat(yDelimit1).concat(dbtLcyL2OutRef).concat(yDelimit1).concat(dbtLcyL2ExpoDate)
                .concat(yDelimit1).concat(dbtLcyL2CcyMkt).concat(yDelimit1).concat(dbtLcyL2TransRef).concat(yDelimit1)
                .concat(dbtLcyL2SysId).concat(yDelimit1).concat(dbtLcyL2BookingDte).concat(yDelimit1).concat(getCurrMarketVal("VAT"))
                .concat(yDelimit1).concat("CSD."+capActNm);
        logger.debug("String Stmt debitLcyL2Entries = "+ debitLcyL2Entries);
        
        // Set the CreditLeg for the AcctEntries1
        String cdtLcyL2Acct = "";
        String cdtLcyL2CompanyCode = "";
        String cdtLcyL2AmtLcy = "";
        String cdtLcyL2TransCode = "";
        String cdtLcyL2PlCateg = "";
        String cdtLcyL2CustId = "";
        String cdtLcyL2AcctOff = "";
        String cdtLcyL2ProdCateg = "";
        String cdtLcyL2ValueDate = "";
        String cdtLcyL2Ccy = "";
        String cdtLcyL2AmtFcy = "";
        String cdtLcyL2ExchgRte = "";
        String cdtLcyL2PosType = "";
        String cdtLcyL2OutRef = "";
        String cdtLcyL2ExpoDate = "";
        String cdtLcyL2CcyMkt = "";
        String cdtLcyL2TransRef = "";
        String cdtLcyL2SysId = "";
        String cdtLcyL2BookingDte = "";
        
        cdtLcyL2Acct = getLcyInternalAcct(dbtLcyL2Acct);
        // Get the Account Record.
        AccountRecord yCdtAcctRecObj = getLegAcctRec(cdtLcyL2PlCateg, cdtLcyL2Acct, yAcctRec);
        cdtLcyL2CompanyCode = arrangementActivityRecord.getCoCode();
        cdtLcyL2AmtLcy = dbtLcyL2AmtLcy;
        cdtLcyL2TransCode = acEntParamRec.getCrTxnCode().getValue();
        cdtLcyL2CustId = yAcctRec.getCustomer().getValue();
        cdtLcyL2AcctOff = yCdtAcctRecObj.getAccountOfficer().getValue();
        cdtLcyL2ProdCateg = yCdtAcctRecObj.getCategory().getValue();
        cdtLcyL2ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        cdtLcyL2Ccy = ySess.getLocalCurrency();
        cdtLcyL2PosType = yCdtAcctRecObj.getPositionType().getValue();
        cdtLcyL2OutRef = acctNumber;
        cdtLcyL2ExpoDate = todayDay;
        cdtLcyL2CcyMkt = yCdtAcctRecObj.getCurrencyMarket().getValue();
        cdtLcyL2TransRef = arrangementContext.getTransactionReference();
        cdtLcyL2SysId = systemId;
        cdtLcyL2BookingDte = todayDay;
        
        // Forming CreditLeg Message
        String creditLcyL2Entries = cdtLcyL2Acct.concat(yDelimit1).concat(cdtLcyL2CompanyCode).concat(yDelimit1)
                .concat(cdtLcyL2AmtLcy).concat(yDelimit1).concat(cdtLcyL2TransCode).concat(yDelimit1).concat(cdtLcyL2PlCateg)
                .concat(yDelimit1).concat(cdtLcyL2CustId).concat(yDelimit1).concat(cdtLcyL2AcctOff).concat(yDelimit1)
                .concat(cdtLcyL2ProdCateg).concat(yDelimit1).concat(cdtLcyL2ValueDate).concat(yDelimit1).concat(cdtLcyL2Ccy)
                .concat(yDelimit1).concat(cdtLcyL2AmtFcy).concat(yDelimit1).concat(cdtLcyL2ExchgRte).concat(yDelimit1)
                .concat(cdtLcyL2PosType).concat(yDelimit1).concat(cdtLcyL2OutRef).concat(yDelimit1).concat(cdtLcyL2ExpoDate)
                .concat(yDelimit1).concat(cdtLcyL2CcyMkt).concat(yDelimit1).concat(cdtLcyL2TransRef).concat(yDelimit1)
                .concat(cdtLcyL2SysId).concat(yDelimit1).concat(cdtLcyL2BookingDte).concat(yDelimit1).concat(getCurrMarketVal("VAT"))
                .concat(yDelimit1).concat("CSD."+capActNm);
        logger.debug("String Stmt creditLcyL2Entries = "+ creditLcyL2Entries);
        
        EbCsdAcctEntDetailsEodRecord acctEntDetsEodVatleg2Rec = new EbCsdAcctEntDetailsEodRecord(this);
        acctEntDetsEodVatleg2Rec.setAccountingEntry(debitLcyL2Entries.concat("$$").concat(creditLcyL2Entries));
        logger.debug("String acctEntDetsEodVatleg1Rec = "+ acctEntDetsEodVatleg2Rec);
        // Forming Unique Reference Number followed with account number
        String uniqueIdVatLeg2 = UUID.randomUUID().toString();
        logger.debug("String uniqueIdVatLeg2 = "+ uniqueIdVatLeg2);
        // Write the details into EB.CSD.ACCT.ENT.DETAILS.EOD
        writeAcctEntEodDets(uniqueIdVatLeg2, acctEntDetsEodVatleg2Rec);
        
    }

    //
    public void updVatLeg1LcyEntDetsEod(EbCsdVatWhtRateParamRecord yVatWhtParamRec,
            AccountRecord yAcctRec, String acctNumber, ArrangementContext arrangementContext, String localCcy, String plCategory, AaArrangementActivityRecord arrangementActivityRecord, String yExcRate) {
        /** String lastWorkingDay = ySess.getCurrentVariable("!LAST.WORKING.DAY"); */
        String todayDay = ySess.getCurrentVariable("!TODAY");
        logger.debug("String Input VAT Leg1 todayDay = "+ todayDay);
        // get AC.ENTRY.PARAM record
        AcEntryParamRecord acEntParamRec = getAcParamRec("VAT");
        String yDelimit1 = "*";
        // Set the debit Leg
        String dbtLcyL1Acct = "";
        String dbtLcyL1CompanyCde = "";
        String dbtLcyL1AmtLcy = "";
        String dbtLcyL1TransCode = "";
        String dbtLcyL1PlCateg = "";
        String dbtLcyL1CustId = "";
        String dbtLcyL1AcctOff = "";
        String dbtLcyL1ProdCateg = "";
        String dbtLeg1ValueDate = "";
        String dbtLcyL1Ccy = "";
        String dbtLcyL1AmtFcy = "";
        String dbtLcyL1ExchgRte = "";
        String dbtLcyL1PosType = "";
        String dbtLcyL1OutRef = "";
        String dbtLcyL1ExpoDate = "";
        String dbtLcyL1CcyMkt = "";
        String dbtLcyL1TransRef = "";
        String dbtLcyL1SysId = "";
        String dbtLcyL1BookingDte = "";
        
        if (yTaxType.equalsIgnoreCase("INPUT")&& !yFcyFlag) {
            dbtLcyL1Acct = getvatPayAcct(yVatWhtParamRec.getInputVatTempAccount().getValue(), localCcy);
            dbtLcyL1Ccy = localCcy;
            dbtLcyL1AmtLcy = vatCalcAmt;
        } else {
            dbtLcyL1Acct = getvatPayAcct(yVatWhtParamRec.getInputVatTempAccount().getValue(), yAcctRec.getCurrency().getValue());
            dbtLcyL1Ccy = yAcctRec.getCurrency().getValue();
            dbtLcyL1AmtFcy = vatCalcAmt;
            dbtLcyL1ExchgRte = getDefExchgRate(yAcctRec.getCurrency().getValue());
            dbtLcyL1AmtLcy = getCalcAmtLcy(dbtLcyL1AmtFcy, dbtLcyL1ExchgRte);
        }
        
        AccountRecord yDebAcctRecObj = getLegAcctRec(dbtLcyL1PlCateg, dbtLcyL1Acct, yAcctRec);
        dbtLcyL1CompanyCde = arrangementActivityRecord.getCoCode(); 
        dbtLcyL1TransCode = acEntParamRec.getDrTxnCode().getValue();
        dbtLcyL1CustId = yAcctRec.getCustomer().getValue();
        dbtLcyL1AcctOff = yDebAcctRecObj.getAccountOfficer().getValue();
        dbtLcyL1ProdCateg = yDebAcctRecObj.getCategory().getValue();
        dbtLeg1ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        dbtLcyL1PosType = yDebAcctRecObj.getPositionType().getValue();
        dbtLcyL1OutRef = acctNumber;
        dbtLcyL1ExpoDate = todayDay;
        dbtLcyL1CcyMkt = yDebAcctRecObj.getCurrencyMarket().getValue();
        dbtLcyL1TransRef = arrangementContext.getTransactionReference();
        dbtLcyL1SysId = systemId;
        dbtLcyL1BookingDte = todayDay;
        
        // Forming DebitLeg Message
        String debitLcyL1Entries = dbtLcyL1Acct.concat(yDelimit1).concat(dbtLcyL1CompanyCde).concat(yDelimit1)
                .concat(dbtLcyL1AmtLcy).concat(yDelimit1).concat(dbtLcyL1TransCode).concat(yDelimit1).concat(dbtLcyL1PlCateg)
                .concat(yDelimit1).concat(dbtLcyL1CustId).concat(yDelimit1).concat(dbtLcyL1AcctOff).concat(yDelimit1)
                .concat(dbtLcyL1ProdCateg).concat(yDelimit1).concat(dbtLeg1ValueDate).concat(yDelimit1).concat(dbtLcyL1Ccy)
                .concat(yDelimit1).concat(dbtLcyL1AmtFcy).concat(yDelimit1).concat(dbtLcyL1ExchgRte).concat(yDelimit1)
                .concat(dbtLcyL1PosType).concat(yDelimit1).concat(dbtLcyL1OutRef).concat(yDelimit1).concat(dbtLcyL1ExpoDate)
                .concat(yDelimit1).concat(dbtLcyL1CcyMkt).concat(yDelimit1).concat(dbtLcyL1TransRef).concat(yDelimit1)
                .concat(dbtLcyL1SysId).concat(yDelimit1).concat(dbtLcyL1BookingDte).concat(yDelimit1).concat("").concat(yDelimit1)
                .concat("CSD."+capActNm);
        logger.debug("String Stmt debitLcyL1Entries = "+ debitLcyL1Entries);
        
        // Set the CreditLeg for the AcctEntries1
        String cdtLcyL1Acct = "";
        String cdtLcyL1CompanyCode = "";
        String cdtLcyL1AmtLcy = "";
        String cdtLcyL1TransCode = "";
        String cdtLcyL1PlCateg = "";
        String cdtLcyL1CustId = "";
        String cdtLcyL1AcctOff = "";
        String cdtLcyL1ProdCateg = "";
        String cdtLcyL1ValueDate = "";
        String cdtLcyL1Ccy = "";
        String cdtLcyL1AmtFcy = "";
        String cdtLcyL1ExchgRte = "";
        String cdtLcyL1PosType = "";
        String cdtLcyL1OutRef = "";
        String cdtLcyL1ExpoDate = "";
        String cdtLcyL1CcyMkt = "";
        String cdtLcyL1TransRef = "";
        String cdtLcyL1SysId = "";
        String cdtLcyL1BookingDte = "";
        
        if (yTaxType.equalsIgnoreCase("INPUT")) {
            if (inputVatCalcType.equalsIgnoreCase("Inclusive") && !yFcyFlag) {
                cdtLcyL1PlCateg = plCategory;
                cdtLcyL1Ccy = localCcy;
                cdtLcyL1AmtLcy = dbtLcyL1AmtLcy;
            } else if (inputVatCalcType.equalsIgnoreCase("Exclusive") || yFcyFlag) {
                cdtLcyL1Acct = getvatPayAcct(yVatWhtParamRec.getVatPayableAccount().getValue(), dbtLcyL1Ccy);
                cdtLcyL1Ccy = dbtLcyL1Ccy;
                if (yFcyFlag) {
                    cdtLcyL1AmtFcy = dbtLcyL1AmtFcy;
                    cdtLcyL1ExchgRte = dbtLcyL1ExchgRte;
                    cdtLcyL1AmtLcy = dbtLcyL1AmtLcy;
                }
            }
        }
        
        // Get the Account Record.
        AccountRecord yCdtAcctRecObj = getLegAcctRec(cdtLcyL1PlCateg, cdtLcyL1Acct, yAcctRec);
        cdtLcyL1CompanyCode = arrangementActivityRecord.getCoCode();
        cdtLcyL1TransCode = acEntParamRec.getCrTxnCode().getValue();
        cdtLcyL1CustId = yAcctRec.getCustomer().getValue();
        cdtLcyL1AcctOff = yCdtAcctRecObj.getAccountOfficer().getValue();
        cdtLcyL1ProdCateg = yCdtAcctRecObj.getCategory().getValue();
        cdtLcyL1ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        cdtLcyL1PosType = yCdtAcctRecObj.getPositionType().getValue();
        cdtLcyL1OutRef = acctNumber;
        cdtLcyL1ExpoDate = todayDay;
        cdtLcyL1CcyMkt = yCdtAcctRecObj.getCurrencyMarket().getValue();
        cdtLcyL1TransRef = arrangementContext.getTransactionReference();
        cdtLcyL1SysId = systemId;
        cdtLcyL1BookingDte = todayDay;
        
        // Forming CreditLeg Message
        String creditLcyL1Entries = cdtLcyL1Acct.concat(yDelimit1).concat(cdtLcyL1CompanyCode).concat(yDelimit1)
                .concat(cdtLcyL1AmtLcy).concat(yDelimit1).concat(cdtLcyL1TransCode).concat(yDelimit1).concat(cdtLcyL1PlCateg)
                .concat(yDelimit1).concat(cdtLcyL1CustId).concat(yDelimit1).concat(cdtLcyL1AcctOff).concat(yDelimit1)
                .concat(cdtLcyL1ProdCateg).concat(yDelimit1).concat(cdtLcyL1ValueDate).concat(yDelimit1).concat(cdtLcyL1Ccy)
                .concat(yDelimit1).concat(cdtLcyL1AmtFcy).concat(yDelimit1).concat(cdtLcyL1ExchgRte).concat(yDelimit1)
                .concat(cdtLcyL1PosType).concat(yDelimit1).concat(cdtLcyL1OutRef).concat(yDelimit1).concat(cdtLcyL1ExpoDate)
                .concat(yDelimit1).concat(cdtLcyL1CcyMkt).concat(yDelimit1).concat(cdtLcyL1TransRef).concat(yDelimit1)
                .concat(cdtLcyL1SysId).concat(yDelimit1).concat(cdtLcyL1BookingDte).concat(yDelimit1).concat("").concat(yDelimit1)
                .concat("CSD."+capActNm);
        logger.debug("String Stmt creditLcyL1Entries = "+ creditLcyL1Entries);
        
        EbCsdAcctEntDetailsEodRecord acctEntDetsEodVatleg1Rec = new EbCsdAcctEntDetailsEodRecord(this);
        acctEntDetsEodVatleg1Rec.setAccountingEntry(debitLcyL1Entries.concat("$$").concat(creditLcyL1Entries));
        logger.debug("String acctEntDetsEodVatleg1Rec = "+ acctEntDetsEodVatleg1Rec);
        // Forming Unique Reference Number followed with account number
        String uniqueIdVatLeg1 = UUID.randomUUID().toString();
        logger.debug("String uniqueIdVatLeg1 = "+ uniqueIdVatLeg1);
        // Write the details into EB.CSD.ACCT.ENT.DETAILS.EOD
        writeAcctEntEodDets(uniqueIdVatLeg1, acctEntDetsEodVatleg1Rec);
        
    }

    //
    private String getCalcAmtLcy(String dbtLcyL1AmtFcy, String dbtLcyL1ExchgRte) {
        BigDecimal calAmtLcy = BigDecimal.ZERO;
        try {
            calAmtLcy = new BigDecimal(dbtLcyL1AmtFcy).multiply(
                    new BigDecimal(dbtLcyL1ExchgRte)).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            // 
        }
        return calAmtLcy.toString();
    }

    //
    private String getDefExchgRate(String currency) {
        String yDefMidRevRate = "";
        try {
            CurrencyRecord yCcyRecord = new CurrencyRecord(da.getRecord("CURRENCY", currency));
            for (CurrencyMarketClass ccyMkt : yCcyRecord.getCurrencyMarket()) {
                if (ccyMkt.getCurrencyMarket().getValue().equals("1")) {
                    yDefMidRevRate = ccyMkt.getMidRevalRate().getValue();
                    break;
                }
            }
        } catch (Exception e) {
            // 
        }
        return yDefMidRevRate;
    }

    //
    private AccountRecord getLegAcctRec(String PlCategory, String AccountNo, AccountRecord acctRecord) {
        if (!PlCategory.isBlank()) {
            return acctRecord;
        } else {
            return new AccountRecord(
                    da.getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(), "ACCOUNT", "", AccountNo));
        }
    }

    //
    private void writeAcctEntEodDets(String uniqueId, EbCsdAcctEntDetailsEodRecord acctEntDetsEodRec) {
        try {
            EbCsdAcctEntDetailsEodTable acctEntDetsTableObj = new EbCsdAcctEntDetailsEodTable(this);
            acctEntDetsTableObj.write(uniqueId, acctEntDetsEodRec);
            logger.debug("String acctEntDetsTableObj = "+ acctEntDetsTableObj);
        } catch (Exception e) {
            logger.debug("Error Message at new Table write = "+ e.toString());
        }
    }

    //
    private AcEntryParamRecord getAcParamRec(String taxTypeIden) {
        AcEntryParamRecord acEntParamRec = new AcEntryParamRecord();
        try {
            EbCsdTaxDetsParamRecord taxDetsPrmRec = new EbCsdTaxDetsParamRecord(da.getRecord(
                    ySess.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.TAX.DETS.PARAM", "", systemNm));
            logger.debug(" String taxTypeIden = "+taxTypeIden);
            String paramId = "";
            if (taxTypeIden.equals("WHT")) {
                logger.debug(" WHT Ac Entry Param condition satisfied ");
                paramId = taxDetsPrmRec.getWhtEntryParam().getValue();
            } else if (taxTypeIden.equals("VAT")) {
                logger.debug(" VAT Ac Entry Param condition satisfied ");
                paramId = taxDetsPrmRec.getVatEntryParam().getValue();
            }
            acEntParamRec = new AcEntryParamRecord(da.getRecord("AC.ENTRY.PARAM", paramId));
            logger.debug(" String acEntParamRec = "+acEntParamRec);
        } catch (Exception e) {
            //
        }
        return acEntParamRec;
    }

    //
    private String getActivityNm(String currActivity) {
        try {
            return currActivity.split("-")[1];
        } catch (Exception e) {
            return "";
        }
    }

    //
    private void updWhtFcyEntDets(EbCsdVatWhtRateParamRecord yVatWhtParamRec, AccountRecord yAcctRec,
            String acctNumber, String yWhtExcRate, ArrangementContext arrangementContext) {

        String ydbSign = "D";
        String ycdSign = "C";
        String ydbTransCde = "0001";
        String ycdTransCde = "0002";
        // Set the Process mode based on lcy and fcy
        String yDelimit1 = ",";
        String processMode = yCsmBulkNm;

        // Set the debit Leg for the AcctEntries2
        String dbtTransCode = "";
        String dbtPlCateg = "";
        String dbtAcctNo = "";
        String dbtAmt = "";
        String dbtCcy = "";
        String dbtTraSign = "";
        String dbtTransRef = "";
        String dbtCustId = "";
        String dbtAccountOfficer = "";
        String dbtProductCateg = "";
        String dbtOurReference = "";

        if (yPayIndication.equalsIgnoreCase("Debit")) {
            dbtTransCode = ycdTransCde;
            dbtTraSign = ycdSign;
        } else {
            dbtTransCode = ydbTransCde;
            dbtTraSign = ydbSign;
        }

        dbtAcctNo = getvatPayAcct(yVatWhtParamRec.getWhtPayableAccount().getValue(), yAcctRec.getCurrency().getValue());
        /** dbtAmt = getExchCcyAmt(yExcRate); */
        dbtAmt = getWhtExchgCcyAmt(yWhtExcRate);
        dbtCcy = yAcctRec.getCurrency().getValue();
        dbtTransRef = arrangementContext.getTransactionReference();
        dbtCustId = yAcctRec.getCustomer().getValue();
        dbtAccountOfficer = yAcctRec.getAccountOfficer().getValue();
        dbtProductCateg = yAcctRec.getCategory().getValue();
        dbtOurReference =  acctNumber;
        // Forming DebitLeg Message
        String debitLegEntries = processMode.concat(yDelimit1).concat(dbtTransCode).concat(yDelimit1).concat(dbtPlCateg)
                .concat(yDelimit1).concat(dbtAcctNo).concat(yDelimit1).concat(dbtAmt).concat(yDelimit1).concat(dbtCcy)
                .concat(yDelimit1).concat(dbtTraSign).concat(yDelimit1).concat(dbtTransRef).concat(yDelimit1)
                .concat(dbtCustId).concat(yDelimit1).concat(dbtAccountOfficer).concat(yDelimit1).concat(dbtProductCateg)
                .concat(yDelimit1).concat(dbtOurReference);

        // Set the CreditLeg for the AcctEntries2
        String cdtTransCode = "";
        String cdtPlCateg = "";
        String cdtAccount = "";
        String cdtAmount = "";
        String cdtCcy = "";
        String cdtTraSign = "";
        String cdtTransRef = "";
        String cdtCustId = "";
        String cdtAccountOfficer = "";
        String cdtProductCateg = "";
        String cdtOurReference = "";

        if (yPayIndication.equalsIgnoreCase("Debit")) {
            cdtTransCode = ydbTransCde;
            cdtTraSign = ydbSign;
        } else {
            cdtTransCode = ycdTransCde;
            cdtTraSign = ycdSign;
        }

        cdtAccount = getLcyInternalAcct(dbtAcctNo);
        cdtAmount = dbtAmt;
        cdtCcy = ySess.getLocalCurrency();
        cdtTransRef = arrangementContext.getTransactionReference();
        cdtCustId = yAcctRec.getCustomer().getValue();
        cdtAccountOfficer = yAcctRec.getAccountOfficer().getValue();
        cdtProductCateg = yAcctRec.getCategory().getValue();
        cdtOurReference = acctNumber;
        // Forming CreditLeg Message
        String creditLegEntries = processMode.concat(yDelimit1).concat(cdtTransCode).concat(yDelimit1).concat(cdtPlCateg)
                .concat(yDelimit1).concat(cdtAccount).concat(yDelimit1).concat(cdtAmount).concat(yDelimit1)
                .concat(cdtCcy).concat(yDelimit1).concat(cdtTraSign).concat(yDelimit1).concat(cdtTransRef)
                .concat(yDelimit1).concat(cdtCustId).concat(yDelimit1).concat(cdtAccountOfficer).concat(yDelimit1)
                .concat(cdtProductCateg).concat(yDelimit1).concat(cdtOurReference);
        EbCsdAcctEntDetailsRecord acctEntDetsRec = new EbCsdAcctEntDetailsRecord(this);
        acctEntDetsRec.setAccountingEntry(debitLegEntries.concat("#").concat(creditLegEntries));
        logger.debug("String FCY Accounting Entries = "+ debitLegEntries.concat("#").concat(creditLegEntries));
        
        // Forming Unique Reference Number followed with account number
        String uniqueRefId = UUID.randomUUID().toString().concat("$WHT");
        // Write the details into EB.CSD.ACCT.ENT.DETAILS
        writeAcctEntDets(uniqueRefId, acctEntDetsRec);

    }

    //
    private String getWhtExchgCcyAmt(String yExcRate) {
        BigDecimal whtExchngRteAmt = BigDecimal.ZERO;
        try {
            BigDecimal ybdWhtOutputVat = new BigDecimal(whtCalcAmt);
            BigDecimal ybdWhtExchngRate = new BigDecimal(yExcRate);
            whtExchngRteAmt = ybdWhtOutputVat.multiply(ybdWhtExchngRate).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            //
        }
        return whtExchngRteAmt.toString();
    }

    //
    private void updVatLeg1EntriesDets(EbCsdVatWhtRateParamRecord yVatWhtParamRec, AccountRecord yAcctRec,
            String acctNumber, ArrangementContext arrangementContext, String localCcy, String plCategory) {

        // Set the Process mode based on lcy and fcy
        String yDelimit1 = ",";
        String processMode = yCsmBulkNm;
        // Set the debit Leg for the AcctEntries1
        String dbtPlCategory = "";
        String dbtAccount = "";
        String dbtAmount = "";
        String dbtCurrency = "";
        String dbtTranSign = "D";
        String dbtTransReference = "";
        String dbtCustomer = "";
        String dbtAcctOfficer = "";
        String dbtProdCateg = "";
        String dbtOurRef = "";
        dbtOurRef = acctNumber;
        if (yTaxType.equalsIgnoreCase("INPUT")&& !yFcyFlag) {
            dbtAccount = getvatPayAcct(yVatWhtParamRec.getInputVatTempAccount().getValue(), localCcy);
            dbtCurrency = localCcy;
            if (inputVatCalcType.equalsIgnoreCase("Inclusive")) {
                processMode = "CSM";
            }
        }else {
            dbtAccount = getvatPayAcct(yVatWhtParamRec.getInputVatTempAccount().getValue(), yAcctRec.getCurrency().getValue());
            dbtCurrency = yAcctRec.getCurrency().getValue();
        }
        dbtAmount = vatCalcAmt;
        dbtTransReference = arrangementContext.getTransactionReference();
        dbtCustomer = yAcctRec.getCustomer().getValue();
        dbtAcctOfficer = yAcctRec.getAccountOfficer().getValue();
        dbtProdCateg = yAcctRec.getCategory().getValue();
        
        // Forming DebitLeg Message
        String debitLegEntries = processMode.concat(yDelimit1).concat("0001").concat(yDelimit1).concat(dbtPlCategory)
                .concat(yDelimit1).concat(dbtAccount).concat(yDelimit1).concat(dbtAmount).concat(yDelimit1)
                .concat(dbtCurrency).concat(yDelimit1).concat(dbtTranSign).concat(yDelimit1).concat(dbtTransReference)
                .concat(yDelimit1).concat(dbtCustomer).concat(yDelimit1).concat(dbtAcctOfficer).concat(yDelimit1)
                .concat(dbtProdCateg).concat(yDelimit1).concat(dbtOurRef);
        
        // Set the CreditLeg for the AcctEntries1
        String cdtPlCategory = "";
        String cdtAccount = "";
        String cdtAmount = "";
        String cdtCurrency = "";
        String cdtTranSign = "C";
        String cdtTransReference = "";
        String cdtCustomer = "";
        String cdtAcctOfficer = "";
        String cdtProdCateg = "";
        String cdtOurRef = "";
        if (yTaxType.equalsIgnoreCase("INPUT")) {
            if (inputVatCalcType.equalsIgnoreCase("Inclusive") && !yFcyFlag) {
                cdtPlCategory = plCategory;
                cdtCurrency = localCcy;
                processMode = "CSM";
            } else if (inputVatCalcType.equalsIgnoreCase("Exclusive") || yFcyFlag) {
                cdtAccount = getvatPayAcct(yVatWhtParamRec.getVatPayableAccount().getValue(), dbtCurrency);
                cdtCurrency = dbtCurrency;
            } 
        }
        cdtAmount = dbtAmount;
        cdtTransReference = arrangementContext.getTransactionReference();
        cdtCustomer = yAcctRec.getCustomer().getValue();
        cdtAcctOfficer = yAcctRec.getAccountOfficer().getValue();
        cdtProdCateg = yAcctRec.getCategory().getValue();
        cdtOurRef = acctNumber;
        // Forming CreditLeg Message
        String creditLegEntries = processMode.concat(yDelimit1).concat("0002").concat(yDelimit1).concat(cdtPlCategory)
                .concat(yDelimit1).concat(cdtAccount).concat(yDelimit1).concat(cdtAmount).concat(yDelimit1)
                .concat(cdtCurrency).concat(yDelimit1).concat(cdtTranSign).concat(yDelimit1).concat(cdtTransReference)
                .concat(yDelimit1).concat(cdtCustomer).concat(yDelimit1).concat(cdtAcctOfficer).concat(yDelimit1)
                .concat(cdtProdCateg).concat(yDelimit1).concat(cdtOurRef).concat("**Leg1");
        
        EbCsdAcctEntDetailsRecord acctEntDetsRec = new EbCsdAcctEntDetailsRecord(this);
        acctEntDetsRec.setAccountingEntry(debitLegEntries.concat("#").concat(creditLegEntries));
        // Forming Unique Reference Number followed with account number
        String uniqueRefId = UUID.randomUUID().toString();
        // Write the details into EB.CSD.ACCT.ENT.DETAILS
        logger.debug("acctleg1 = "+acctEntDetsRec);
        
        writeAcctEntDets(uniqueRefId, acctEntDetsRec);
    }

    //
    private String getWhtRateParamVal(EbCsdVatWhtRateParamRecord yVatWhtParamRec, int bestFitPos) {
        String whtVateRateVal = "";
        if (yBstFitFlag) {
            yTaxType = yVatWhtParamRec.getClientCategory(bestFitPos).getTaxType().getValue();
            yPLType = yVatWhtParamRec.getClientCategory(bestFitPos).getPlType().getValue();
            inputVatCalcType = yVatWhtParamRec.getClientCategory(bestFitPos).getInputVatCalculationType().getValue();
            whtCalcType = yVatWhtParamRec.getClientCategory(bestFitPos).getWhtCalculationType().getValue();
            if (yTaxType.equalsIgnoreCase("Input") && yPLType.equalsIgnoreCase("Interest")) {
                String yVhtRateCond = yVatWhtParamRec.getClientCategory(bestFitPos).getVatRate().getValue();
                String ytaxcode = fetchTaxCodeRec(yVhtRateCond);
                whtVateRateVal = fetchTaxRate(ytaxcode);
                // Get the WHT Rate
                String yWhtRteCond = yVatWhtParamRec.getClientCategory(bestFitPos).getWhtRate().getValue();
                if (!whtCalcAmt.isBlank() && !whtCalcType.isBlank()
                        && !yWhtRteCond.isBlank()) {
                    String yWhtTaxcode = fetchTaxCodeRec(yWhtRteCond);
                    whtRateValue = fetchTaxRate(yWhtTaxcode);
                }
            }
        }
        return whtVateRateVal;
    }

    //
    private String fetchTaxRate(String ytaxcde) {
        String taxRateVal = "";
        try {
            List<String> ytaxCodeList = da.selectRecords("", "TAX", "", "WITH @ID LIKE " + ytaxcde + "....");
            TaxRecord ytaxRec = new TaxRecord(da.getRecord("TAX", getLatestTaxId(ytaxCodeList)));
            taxRateVal = ytaxRec.getRate().getValue();
        } catch (Exception e) {
            //
        }
        return taxRateVal;
    }

    //
    private String getLatestTaxId(List<String> ytaxCodeList) {
        String ylatestTaxId = "";
        if (ytaxCodeList.size() == 1) {
            ylatestTaxId = ytaxCodeList.get(0);
        }
        // if the id list is GT 1, split the date component from the id
        if (ytaxCodeList.size() > 1) {
            List<Integer> datesList = new ArrayList<>();
            String[] ytaxCodeDets = {};
            for (String tempCode : ytaxCodeList) {
                ytaxCodeDets = tempCode.split("\\.");
                datesList.add(Integer.parseInt(ytaxCodeDets[1]));
            }
            if (!datesList.isEmpty()) {
                ylatestTaxId = ytaxCodeDets[0] + "." + Collections.max(datesList).toString();
            }
        }
        return ylatestTaxId;
    }

    //
    private String fetchTaxCodeRec(String whtRateCond) {
        String ytaxcode = "";
        try {
            TaxTypeConditionRecord yTaxTypeCondRec = new TaxTypeConditionRecord(da.getRecord(
                    ySess.getCompanyRecord().getFinancialMne().getValue(), "TAX.TYPE.CONDITION", "", whtRateCond));
            int custTaxGrpLen = yTaxTypeCondRec.getCustTaxGrp().size();
            if (yTaxTypeCondRec.getCustTaxGrp().size() > 0) {
                int ycontractGrpLen = yTaxTypeCondRec.getCustTaxGrp(custTaxGrpLen - 1).getContractGrp().size();
                if (ycontractGrpLen > 0) {
                    ytaxcode = yTaxTypeCondRec.getCustTaxGrp(custTaxGrpLen - 1).getContractGrp(ycontractGrpLen - 1)
                            .getTaxCode().getValue();
                }
            }
        } catch (Exception e) {
            //
        }
        return ytaxcode;
    }
    
    //
    private int checkBestFixPos(String prodCateg, CustomerRecord yCustomerRec, String yPlCategParamRecId) {
        int vatPos = 0;
        String matchCategInd = getBestFitPosition(yCustomerRec, prodCateg, yPlCategParamRecId);
        if (!matchCategInd.equals("")) {
            vatPos = Integer.parseInt(matchCategInd);
        }
        return vatPos;
    }

    //
    private String getBestFitPosition(CustomerRecord yCustomerRec, String prodCateg, String yPlCategParamRecId) {
        String customerSector = yCustomerRec.getSector().getValue();
        String regCtry = yCustomerRec.getRegCountry().getValue();
        String yindex = "";
        String defFitIndex = "";
        try {
            EbCsdVatWhtBestFitUpdRecord bestfitRec = new EbCsdVatWhtBestFitUpdRecord(
                    da.getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.VAT.WHT.BEST.FIT.UPD",
                            "", yPlCategParamRecId));
            String yotherRegionCn = bestfitRec.getOtherRegionCn().getValue();
            String yotherRegionCnPos = bestfitRec.getOtherRegionCnPos().getValue();
            String yregionCn = bestfitRec.getRegionCn().getValue();
            String yregionCnPos = bestfitRec.getRegionCnPos().getValue();

            String[] yarrConfig = (!customerSector.isEmpty() && (regCtry.isEmpty() || !"CN".equals(regCtry)))
                    ? yotherRegionCn.split("#")
                            : yregionCn.split("#");
            String[] arrPos = (!customerSector.isEmpty() && (regCtry.isEmpty() || !"CN".equals(regCtry)))
                    ? yotherRegionCnPos.split("#")
                            : yregionCnPos.split("#");

            for (int i = 0; i < yarrConfig.length; i++) {
                String cfg = yarrConfig[i];
                String[] parts = cfg.split("\\*", -1);
                // get the Default index position, when client category, 
                // Client Location and Product Category is empty
                if (parts[0].isBlank() && parts[1].isBlank()
                        && parts[2].isBlank()) {
                    defFitIndex = arrPos[i];
                }
                // Matching sector
                if (!parts[0].equals(customerSector))
                    continue;

                String yconfigCountry = parts[1]; // may be "", CN, or other
                String yconfigProd = parts[2]; // may be "", prodCateg, or other

                boolean ycountryMatches = yconfigCountry.isEmpty() || // blank allowed
                        "CN".equals(yconfigCountry) || // CN allowed
                        !yconfigCountry.equals("CN"); // non-CN allowed (original logic) -->Means Any other cntry
                boolean yproductMatches = yconfigProd.isEmpty() || // blank allowed
                        yconfigProd.equals(prodCateg); // exact match

                if (ycountryMatches && yproductMatches) {
                    yindex = arrPos[i];
                    yBstFitFlag = true;
                    break;
                }
            }
            if (!yBstFitFlag) {
                yindex = defFitIndex;
                yBstFitFlag = true;
            }
        } catch (Exception e) {
            //
        }
        return yindex;
    }

    //
    private EbCsdVatWhtRateParamRecord getVatWhtParamRecord(String yPlCategParamRecId) {
        try {
            return new EbCsdVatWhtRateParamRecord(da.getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                    "EB.CSD.VAT.WHT.RATE.PARAM", "", yPlCategParamRecId));
        } catch (Exception e) {
            return new EbCsdVatWhtRateParamRecord(this);
        }
    }

    //
    private String checkPLCategParam(String plCategory) {
        boolean yParamIdFlag = false;
        String yVatWhtParamId = "";
        EbCsdStorePlCategParamRecord yPlCategParamRec = new EbCsdStorePlCategParamRecord(da.getRecord(
                ySess.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.STORE.PL.CATEG.PARAM", "", systemNm));
        for (TField plCategValue : yPlCategParamRec.getPlCategParam()) {
            String rngStart = "";
            String rngEnd = "";
            try {
                if (plCategValue.getValue().contains("-")) {
                    rngStart = plCategValue.getValue().split("-")[0];
                    rngEnd = plCategValue.getValue().split("-")[1];
                    if (Integer.parseInt(plCategory) >= Integer.parseInt(rngStart)
                            && Integer.parseInt(plCategory) <= Integer.parseInt(rngEnd)) {
                        yVatWhtParamId = plCategValue.getValue();
                        yParamIdFlag = true;
                    }
                } else if (!plCategValue.getValue().contains("-")
                        && (Integer.parseInt(plCategory) >= Integer.parseInt(plCategValue.getValue())
                        && Integer.parseInt(plCategory) <= Integer.parseInt(plCategValue.getValue()))) {
                    yVatWhtParamId = plCategValue.getValue();
                    yParamIdFlag = true;
                }
            } catch (Exception e) {
                //
            }
            if (yParamIdFlag) {
                break;
            }
        }
        return yVatWhtParamId;
    }

    //
    private String getPlCategFromAccounting(String intPropName) {
        String yPlCategoryId = "";
        try {
            // get the propertyId for the Accounting property class
            List<String> accountingPropList = yCon.getPropertyIdsForPropertyClass("ACCOUNTING");
            TStructure tAcctStruct = yCon.getConditionForProperty(accountingPropList.get(0));
            // get the pl category from the AA.PRD.DES.ACCOUNTING fot the Respective Tax
            // Property
            AaPrdDesAccountingRecord accountingRec = new AaPrdDesAccountingRecord(tAcctStruct);
            for (com.temenos.t24.api.records.aaprddesaccounting.PropertyClass yProperty : accountingRec.getProperty()) {
                if (yProperty.getProperty().getValue().equals(intPropName)) {
                    yPlCategoryId = yProperty.getBookingCm().getValue();
                    break;
                }
            }
        } catch (Exception e) {
            //
        }
        return yPlCategoryId;
    }

    //
    private void updVatLeg2EntriesDets(EbCsdVatWhtRateParamRecord yVatWhtParamRec, AccountRecord yAcctRec,
            String acctNumber, String yVatExcRate, ArrangementContext arrangementContext, String localCcy) {
        // If Local Currency, no need to raise entries
        // Set the Process mode based on lcy and fcy
        String yDelimit1 = ",";
        String processMode = yCsmBulkNm;

        // Set the debit Leg for the AcctEntries2
        String dbtPlCateg = "";
        String dbtAcctNo = "";
        String dbtAmt = "";
        String dbtCcy = "";
        String dbtTransRef = "";
        String dbtCustId = "";
        String dbtAccountOfficer = "";
        String dbtProductCateg = "";
        String dbtOurReference = "";

        dbtAcctNo = getvatPayAcct(yVatWhtParamRec.getVatPayableAccount().getValue(), yAcctRec.getCurrency().getValue());
        dbtAmt = getExchCcyAmt(yVatExcRate);
        dbtCcy = localCcy;
        dbtTransRef = arrangementContext.getTransactionReference();
        dbtCustId = yAcctRec.getCustomer().getValue();
        dbtAccountOfficer = yAcctRec.getAccountOfficer().getValue();
        dbtProductCateg = yAcctRec.getCategory().getValue();
        dbtOurReference = acctNumber;
        // Forming DebitLeg Message
        String debitLegEntries = processMode.concat(yDelimit1).concat("0001").concat(yDelimit1).concat(dbtPlCateg)
                .concat(yDelimit1).concat(dbtAcctNo).concat(yDelimit1).concat(dbtAmt).concat(yDelimit1).concat(dbtCcy)
                .concat(yDelimit1).concat("D").concat(yDelimit1).concat(dbtTransRef).concat(yDelimit1).concat(dbtCustId)
                .concat(yDelimit1).concat(dbtAccountOfficer).concat(yDelimit1).concat(dbtProductCateg).concat(yDelimit1)
                .concat(dbtOurReference);

        // Set the CreditLeg for the AcctEntries2
        String cdtPlCateg = "";
        String cdtAccount = "";
        String cdtAmount = "";
        String cdtCcy = "";
        String cdtTransRef = "";
        String cdtCustId = "";
        String cdtAccountOfficer = "";
        String cdtProductCateg = "";
        String cdtOurReference = "";

        cdtAccount = getLcyInternalAcct(dbtAcctNo);
        cdtAmount = dbtAmt;
        cdtCcy = localCcy;
        cdtTransRef = arrangementContext.getTransactionReference();
        cdtCustId = yAcctRec.getCustomer().getValue();
        cdtAccountOfficer = yAcctRec.getAccountOfficer().getValue();
        cdtProductCateg = yAcctRec.getCategory().getValue();
        cdtOurReference = acctNumber;
        // Forming CreditLeg Message
        String creditLegEntries = processMode.concat(yDelimit1).concat("0002").concat(yDelimit1).concat(cdtPlCateg)
                .concat(yDelimit1).concat(cdtAccount).concat(yDelimit1).concat(cdtAmount).concat(yDelimit1)
                .concat(cdtCcy).concat(yDelimit1).concat("C").concat(yDelimit1).concat(cdtTransRef).concat(yDelimit1)
                .concat(cdtCustId).concat(yDelimit1).concat(cdtAccountOfficer).concat(yDelimit1).concat(cdtProductCateg)
                .concat(yDelimit1).concat(cdtOurReference).concat("**Leg2");
        
        EbCsdAcctEntDetailsRecord acctEntDetsRec = new EbCsdAcctEntDetailsRecord(this);
        acctEntDetsRec.setAccountingEntry(debitLegEntries.concat("#").concat(creditLegEntries));
        // Forming Unique Reference Number followed with account number
        String uniqueRefId = UUID.randomUUID().toString();
        logger.debug("acctleg2 = "+acctEntDetsRec);
        // Write the details into EB.CSD.ACCT.ENT.DETAILS
        writeAcctEntDets(uniqueRefId, acctEntDetsRec);
        
    }

    //
    private String getvatPayAcct(String vatPayableAcct, String localCurrency) {
        String intAcctNum = "";
        for (String yIntAcctNo : da.getConcatValues("CATEG.INT.ACCT", vatPayableAcct)) {
            try {
                AccountRecord yIntAcctRec = getAcctRecord(yIntAcctNo);
                if (yIntAcctRec.getCurrency().getValue().equals(localCurrency)) {
                    intAcctNum = yIntAcctNo;
                    break;
                }
            } catch (Exception e) {
                //
            }
        }
        return intAcctNum;
    }

    //
    private String getLcyInternalAcct(String dbtAcctNo) {
        String intLcyAcct = "";
        try {
            String ylocalCcy = ySess.getLocalCurrency(); // get the local currency
            String ySubDivCode = ySess.getCompanyRecord().getSubDivisionCode().getValue();
            String yCategId = dbtAcctNo.substring(3, 8); // Get the Category
            String lcyIntAcctId = ylocalCcy.concat(yCategId).concat(ySubDivCode);
            List<String> categIntAcctList = da.getConcatValues("CATEG.INT.ACCT", yCategId);
            intLcyAcct = categIntAcctList.stream().filter(code -> code.startsWith(lcyIntAcctId)).findFirst().orElse(""); // Get
            // the
            if (intLcyAcct.isEmpty()) {
                logger.debug("*** getLcyInternalAcct method internalLcyAcct is empty ");
            String lcyIntCatAcctId = ylocalCcy.concat(yCategId);
            logger.debug("*** getLcyInternalAcct method internalLcyAcct is empty case lcyIntCatAcctId" +lcyIntCatAcctId);
            logger.debug("String lcyIntAcctId = "+ lcyIntCatAcctId);
            
            intLcyAcct = categIntAcctList.stream().filter(code -> code.startsWith(lcyIntCatAcctId)).findFirst()
                    .orElse(""); // Get the internal account for the respective currency
            logger.debug("*** getLcyInternalAcct method internalLcyAcct is empty case internalLcyAcct" +intLcyAcct);
            }
        } catch (Exception e) {
            //
        }
        return intLcyAcct;
    }

    //
    private void writeAcctEntDets(String uniqueRefId, EbCsdAcctEntDetailsRecord acctEntDetsRec) {
        try {
            EbCsdAcctEntDetailsTable acctEntDetsTable = new EbCsdAcctEntDetailsTable(this);
            acctEntDetsTable.write(uniqueRefId, acctEntDetsRec);
        } catch (Exception e) {
            //
        }
    }

    //
    private void writeDetstoLiveTable(EbCsdTaxDetailsUpdRecord yTaxDetsRec, String acctNumber) {
        try {
            EbCsdTaxDetailsUpdTable yTaxDetsTab = new EbCsdTaxDetailsUpdTable(this);
            yTaxDetsTab.write(acctNumber, yTaxDetsRec);
        } catch (Exception e) {
            //
        }
    }

    //
    public EbCsdTaxDetailsUpdRecord updVatInterestDets(EbCsdTaxDetailsUpdRecord yTaxDetsRec, AccountRecord yAcctRec,
            String taxRateVal, boolean yFcyFlag, String yVatExcRate, String intPropName) {
        int totalIntSize = yTaxDetsRec.getInterestDetail().size();
        logger.debug("String totalIntSize = "+totalIntSize);
        if (totalIntSize == 0) {
            InterestDetailClass yInterestDets = new InterestDetailClass();
            yInterestDets.setInterestDetail(intPropName);
            yInterestDets.setInterestTransactionType(EXPENSE);
            yInterestDets.setInterestCurrency(yAcctRec.getCurrency().getValue());
            yInterestDets.setInputVatRateForInterest(taxRateVal);
            setVatExchgRate(yFcyFlag, yInterestDets, yVatExcRate);
            if (!whtCalcType.isBlank() && !whtCalcAmt.isBlank()) {
                yInterestDets.setWhtRateForInterest(whtRateValue);
            }
            PeriodStartClass yPeriodStartObj = new PeriodStartClass();
            updPeriodStartDte(yPeriodStartObj, intPropName, yAcctRec);
            yPeriodStartObj.setTotalIntAmount(creditIntAmt);
            // Update the Interest Transaction Type
            updateVatAmt(yFcyFlag, yPeriodStartObj, yVatExcRate);
            // Set the Period Start class in the first position
            yInterestDets.setPeriodStart(yPeriodStartObj, 0);
            logger.debug("String yInterestDets = "+yInterestDets);
            yTaxDetsRec.setInterestDetail(yInterestDets, 0);
            logger.debug("String yTaxDetsRec = "+yTaxDetsRec);
        } else {
            boolean tIntDetsFlg = false;
            for (InterestDetailClass tFIntDets : yTaxDetsRec.getInterestDetail()) {
                if (tFIntDets.getInterestDetail().getValue().equals(intPropName)) {
                    PeriodStartClass perStartObj = new PeriodStartClass();
                    updPeriodStartDte(perStartObj, intPropName, yAcctRec);
                    perStartObj.setTotalIntAmount(creditIntAmt);
                    // Update the Interest Transaction Type
                    updateVatAmt(yFcyFlag, perStartObj, yVatExcRate);
                    tFIntDets.setPeriodStart(perStartObj, tFIntDets.getPeriodStart().size());
                    logger.debug("String tFIntDets = "+tFIntDets);
                    logger.debug("String yTaxDetsRec = "+yTaxDetsRec);
                    tIntDetsFlg = true;
                    break;
                }
            }
            // update new Interest Details
            if (!tIntDetsFlg) {
                InterestDetailClass intDetails = new InterestDetailClass();
                intDetails.setInterestDetail(intPropName);
                intDetails.setInterestTransactionType(EXPENSE);
                intDetails.setInterestCurrency(yAcctRec.getCurrency().getValue());
                intDetails.setInputVatRateForInterest(taxRateVal);
                setVatExchgRate(yFcyFlag, intDetails, yVatExcRate);
                if (!whtCalcType.isBlank() && !whtCalcAmt.isBlank()) {
                    intDetails.setWhtRateForInterest(whtRateValue);
                }
                PeriodStartClass yPerStartObj = new PeriodStartClass();
                updPeriodStartDte(yPerStartObj, intPropName, yAcctRec);
                yPerStartObj.setTotalIntAmount(creditIntAmt);
                // Update the Interest Transaction Type
                updateVatAmt(yFcyFlag, yPerStartObj, yVatExcRate);
                // Set the Period Start class in the first position
                intDetails.setPeriodStart(yPerStartObj, 0);
                logger.debug("String yInterestDets = "+intDetails);
                yTaxDetsRec.setInterestDetail(intDetails, yTaxDetsRec.getInterestDetail().size());
                logger.debug("String yTaxDetsRec = "+yTaxDetsRec);
            }
        }
        return yTaxDetsRec;

    }

    //
    private PeriodStartClass updateVatAmt(boolean yFcyFlag, PeriodStartClass perStartObj, String yExcRate) {
        if (!yFcyFlag) {
            perStartObj.setTotInputValLcyAmtForInt(vatCalcAmt);
        } else {
            perStartObj.setTotInputValLcyAmtForInt(getExchCcyAmt(yExcRate));
            perStartObj.setTotInputVatFcyAmtForInt(vatCalcAmt);
            /** perStartObj.setTotInputValLcyAmtForInt(vatCalcAmt);
            perStartObj.setTotInputVatFcyAmtForInt(getExchCcyAmt(yExcRate)); */
        }
        if (!whtCalcType.isBlank() && !whtCalcAmt.isBlank()) {
            setWhtAmount(yFcyFlag, perStartObj, yExcRate);
        }
        return perStartObj;
    }

    //
    private PeriodStartClass setWhtAmount(boolean yFcyFlag2, PeriodStartClass perStartObj, String yExcRate) {
        if (!yFcyFlag) {
            perStartObj.setTotWhtLcyAmtForInt(whtCalcAmt);
        } else {
            perStartObj.setTotWhtLcyAmtForInt(getWhtExchgCcyAmt(yExcRate));
            perStartObj.setTotWhtFcyAmtForInt(whtCalcAmt);
            /** perStartObj.setTotWhtLcyAmtForInt(whtCalcAmt);
            perStartObj.setTotWhtFcyAmtForInt(getWhtExchgCcyAmt(yExcRate)); */
        }
        return perStartObj;
    }

    //
    private String getExchCcyAmt(String yExcRate) {
        BigDecimal yExchngRateCcyAmt = BigDecimal.ZERO;
        try {
            BigDecimal yBdOutputVat = new BigDecimal(vatCalcAmt);
            BigDecimal yBdExchngRate = new BigDecimal(yExcRate);
            yExchngRateCcyAmt = yBdOutputVat.multiply(yBdExchngRate).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            //
        }
        return yExchngRateCcyAmt.toString();
    }

    //
    private void setVatExchgRate(boolean yFcyFlag, InterestDetailClass yInterestDets, String yExcRate) {
        if (yFcyFlag) {
            yInterestDets.setVatExchangeRateForInterest(yExcRate);
            if (!whtCalcType.isBlank() && !whtCalcAmt.isBlank()) {
                yInterestDets.setWhtExchangeRateForInterest(yExcRate);
            }
        }
    }

    //
    private PeriodStartClass updPeriodStartDte(PeriodStartClass yPeriodStartObj, String intPropName,
            AccountRecord yAcctRec) {
        // Update the total interest details from the AA.INTEREST.ACCRUAL
        String yIntAccId = yAcctRec.getArrangementId().getValue().concat("-").concat(intPropName);
        AaInterestAccrualsRecord yIntAccRec = new AaInterestAccrualsRecord(da.getRecord(
                ySess.getCompanyRecord().getFinancialMne().getValue(), "AA.INTEREST.ACCRUALS", "", yIntAccId));
        for (com.temenos.t24.api.records.aainterestaccruals.PeriodStartClass yIntPerAcc : yIntAccRec.getPeriodStart()) {
            if (yIntPerAcc.getPeriodEnd().getValue().equals(actEffectiveDte)) {
                yPeriodStartObj.setPeriodStart(yIntPerAcc.getPeriodStart().getValue());
                yPeriodStartObj.setPeriodEnd(yIntPerAcc.getPeriodEnd().getValue());
                break;
            }
        }
        return yPeriodStartObj;
    }

    //
    private EbCsdTaxDetailsUpdRecord checkLiveTableRec(String acctNumber, AccountRecord yAcctRecord,
            CustomerRecord yCustomerRec, String yCustomerId) {
        EbCsdTaxDetailsUpdRecord taxDetsUpdRecordObj = new EbCsdTaxDetailsUpdRecord(this);
        try {
            taxDetsUpdRecordObj = new EbCsdTaxDetailsUpdRecord(da.getRecord(
                    ySess.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.TAX.DETAILS.UPD", "", acctNumber));
            updateClientId(taxDetsUpdRecordObj, yCustomerRec, yCustomerId);
        } catch (Exception e) {
            ClientBrdIdClass custBrdIdObj = new ClientBrdIdClass();
            custBrdIdObj.setClientBrdId(yCustomerId);
            custBrdIdObj.setClientCategory(yCustomerRec.getSector().getValue());
            custBrdIdObj.setClientLocation(yCustomerRec.getRegCountry().getValue());
            taxDetsUpdRecordObj.setClientBrdId(custBrdIdObj, 0);
            taxDetsUpdRecordObj.setAccountNumber(acctNumber);
            taxDetsUpdRecordObj.setArrangementNumber(yAcctRecord.getArrangementId().getValue());
            taxDetsUpdRecordObj.setAccountCategory(yAcctRecord.getCategory().getValue());
            taxDetsUpdRecordObj.setAccountCurrency(yAcctRecord.getCurrency().getValue());
        }
        return taxDetsUpdRecordObj;
    }

    //
    private EbCsdTaxDetailsUpdRecord updateClientId(EbCsdTaxDetailsUpdRecord taxDetsUpdRecordObj,
            CustomerRecord yCustomerRec, String yCustomerId) {
        boolean custIdFlg = false;
        for (ClientBrdIdClass CustBrdIdSet : taxDetsUpdRecordObj.getClientBrdId()) {
            if (CustBrdIdSet.getClientBrdId().getValue().equals(yCustomerId)) {
                custIdFlg = true;
                break;
            }
        }
        if (!custIdFlg) {
            ClientBrdIdClass yCltBrdIdSet = new ClientBrdIdClass();
            yCltBrdIdSet.setClientBrdId(yCustomerId);
            yCltBrdIdSet.setClientCategory(yCustomerRec.getSector().getValue());
            yCltBrdIdSet.setClientLocation(yCustomerRec.getRegCountry().getValue());
            taxDetsUpdRecordObj.setClientBrdId(yCltBrdIdSet, taxDetsUpdRecordObj.getClientBrdId().size());
        }
        return taxDetsUpdRecordObj;
    }

    //
    private CustomerRecord getCustomerRec(String yCustomerId) {
        try {
            return new CustomerRecord(
                    da.getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(), "CUSTOMER", "", yCustomerId));
        } catch (Exception e) {
            return new CustomerRecord(this);
        }
    }

    //
    private AccountRecord getAcctRecord(String accountNumber) {
        try {
            return new AccountRecord(
                    da.getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(), "ACCOUNT", "", accountNumber));
        } catch (Exception e) {
            return new AccountRecord(this);
        }
    }

    //
    private String getAccountId(String yArrId) {
        String yAcctNumber = "";
        try {
            AaArrangementRecord yArrRec = new AaArrangementRecord(da.getRecord("AA.ARRANGEMENT", yArrId));
            yAcctNumber = yArrRec.getLinkedAppl(0).getLinkedApplId().getValue();
        } catch (Exception e) {
            //
        }
        return yAcctNumber;
    }

    //
    private String calExhangeRate(String acctCurr, boolean yFcyFlag, String taxIden) {
        String yExchangeRate = "";
        if (yFcyFlag) {
            CurrencyRecord yCurrency = new CurrencyRecord(da.getRecord("CURRENCY", acctCurr));
            yExchangeRate = getExcRate(yCurrency, getCurrMarketVal(taxIden));
        }
        return yExchangeRate;
    }

    //
    private String getExcRate(CurrencyRecord yCurrency, String currMarketVal) {
        String yExcRate = "";
        for (CurrencyMarketClass currMarket : yCurrency.getCurrencyMarket()) {
            if (currMarket.getCurrencyMarket().getValue().equals(currMarketVal)) {
                yExcRate = currMarket.getMidRevalRate().getValue();
                break;
            }
        }
        return yExcRate;
    }

    //
    private String getCurrMarketVal(String taxIden) {
        String currMarkId = "";
        try {
            EbCsdTaxDetsParamRecord ytaxDetsParamRec = new EbCsdTaxDetsParamRecord(da.getRecord(
                    ySess.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.TAX.DETS.PARAM", "", systemNm));
            String acparamId = "";
            if (taxIden.equals("WHT")) {
                acparamId = ytaxDetsParamRec.getWhtEntryParam().getValue();
            } else if (taxIden.equals("VAT")) {
                acparamId = ytaxDetsParamRec.getVatEntryParam().getValue();
            }
            AcEntryParamRecord yEntryParamRec = new AcEntryParamRecord(
                    da.getRecord("AC.ENTRY.PARAM", acparamId));
            currMarkId = yEntryParamRec.getCurrencyMarket().getValue();
        } catch (Exception e) {
            //
        }
        return currMarkId;
    }

    //
    private boolean getFcyTransFlag(String acctCurr, String localCcy) {
        boolean yFcyFlg = false;
        if (!acctCurr.equals(localCcy)) {
            yFcyFlg = true;
        }
        return yFcyFlg;
    }

    //
    private void getCalcTaxAmount(String intPropName, ArrangementContext arrangementContext,
            AaAccountDetailsRecord accountDetailRecord) {
        String currAAAId = arrangementContext.getTransactionReference();
        String actEffDate = arrangementContext.getActivityEffectiveDate();
        for (BillPayDateClass tBillPayDate : accountDetailRecord.getBillPayDate()) {
            if (tBillPayDate.getBillPayDate().getValue().equals(actEffDate)) {
                for (BillIdClass tBillId : tBillPayDate.getBillId()) {
                    if (tBillId.getActivityRef().getValue().equals(currAAAId) && !yIntTaxDetsFlg) {
                        logger.debug("BillId = "+tBillId);
                        String yBillIdVal = tBillId.getBillId().getValue();
                        // Check Credit Interest and Tax Details available in AA.BILL.DETAILS
                        checkIntAndTaxDets(intPropName, yBillIdVal);
                        if (!creditIntAmt.isBlank()) {
                            yIntTaxDetsFlg = true;
                            actEffectiveDte = actEffDate;
                            break;
                        }
                    }
                }
            }
        }
    }

    //
    private void checkIntAndTaxDets(String intPropName, String billId) {
        try {
            List<String> taxPropertyId = yCon.getPropertyIdsForPropertyClass("TAX");
            logger.debug("String taxPropertyId = "+ taxPropertyId);
            TStructure tTaxStruct = yCon.getConditionForProperty(taxPropertyId.get(0));
            AaPrdDesTaxRecord taxCondRec = new AaPrdDesTaxRecord(tTaxStruct);
            String idcomp2 = taxCondRec.getIdComp2().getValue();
            logger.debug("String idcomp2 = "+ idcomp2);
            String taxPropId = intPropName+"-"+idcomp2;
            logger.debug("String taxPropId = "+ taxPropId);

            AaBillDetailsRecord yBillDetailsRec = new AaBillDetailsRecord(da.getRecord("AA.BILL.DETAILS", billId));
            yPayIndication = yBillDetailsRec.getPaymentIndicator().getValue();
            for (PropertyClass tPropertyId : yBillDetailsRec.getProperty()) {
                if (tPropertyId.getProperty().getValue().equals(intPropName)) {
                    creditIntAmt = tPropertyId.getOrPropAmount().getValue();
                }
                if (tPropertyId.getProperty().getValue().equals(taxPropId)) {
                    whtCalcAmt = tPropertyId.getOrPropAmount().getValue();
                }
            }
            logger.debug("String whtCalcAmt = "+ whtCalcAmt);
            logger.debug("String creditIntAmt = "+ creditIntAmt);
        } catch (Exception e) {
            //
        }
    }

    //
    private String calculateInputVat(String creditIntAmt,
            String vatTaxRateVal) {
        String returnResult = "";
        BigDecimal vatAmt;
        boolean vatEmpty = checkVatEmpty();
        logger.debug("String vatEmpty = "+vatEmpty);
        boolean whtEmpty = checkWhtEmpty();
        logger.debug("String whtEmpty = "+whtEmpty);
        BigDecimal grossAmt;
        grossAmt = new BigDecimal(creditIntAmt);
        logger.debug("String grossAmtBd = "+grossAmt);
        if (!vatEmpty && whtEmpty) {
            logger.debug(" Input VAT Alone Calc Condition Configured ");
            vatAmt = calculateOnlyInpVat(grossAmt, vatTaxRateVal);
            logger.debug("String vatAmt ="+vatAmt);
            returnResult = vatAmt.abs().toString();
        }
        if (!vatEmpty && !whtEmpty) {
            logger.debug(" Input VAT + WHT Calc Condition Configured ");
            vatAmt = calculateCombInpVat(grossAmt, vatTaxRateVal);
            logger.debug("String vatAmt ="+vatAmt);
            returnResult = vatAmt.abs().toString();
        }
        return returnResult;
    }

    //
    private BigDecimal calculateCombInpVat(BigDecimal grossAmt, String vatTaxRateVal) {
        VatWhtResult result = null;
        BigDecimal vatAmount = BigDecimal.ZERO;
        try {
            BigDecimal vatRate = new BigDecimal(vatTaxRateVal);
            BigDecimal whtRate = new BigDecimal(whtRateValue);
            if (inputVatCalcType.equals(INCLUSIVE) && whtCalcType.equals(EXCLUSIVE)) {
                result = CsdWithStandTaxCalc.calculateVatWhtIncExc(grossAmt, vatRate, whtRate);
                vatAmount = result.vatAmount;
            }
            if (inputVatCalcType.equals(INCLUSIVE) && whtCalcType.equals(INCLUSIVE)) {
                result = CsdWithStandTaxCalc.calculateVatWhtInclusive(grossAmt, vatRate, whtRate); //
                vatAmount = result.vatAmount;
            }
            if (inputVatCalcType.equals(EXCLUSIVE) && whtCalcType.equals(INCLUSIVE)) {
                result = CsdWithStandTaxCalc.calculateVatWhtExcInc(grossAmt, vatRate, whtRate); //
                vatAmount = result.vatAmount;
            }
            if (inputVatCalcType.equals(EXCLUSIVE) && whtCalcType.equals(EXCLUSIVE)) {
                result = CsdWithStandTaxCalc.calculateVatWhtExclusive(grossAmt, vatRate, whtRate);
                vatAmount = result.vatAmount;
            }
        } catch (Exception e) {
            //
        }
        return vatAmount;
    }

    //
    private boolean checkWhtEmpty() {
        return whtCalcType == null || whtCalcType.isBlank();
    }

    //
    private boolean checkVatEmpty() {
        return inputVatCalcType == null || inputVatCalcType.isBlank();
    }

    private BigDecimal calculateOnlyInpVat(BigDecimal grossAmt, String vatTaxRateVal) {
        BigDecimal formulaVal = BigDecimal.ZERO;
        BigDecimal hundred = new BigDecimal("100");
        BigDecimal one = new BigDecimal("1");
        BigDecimal vatRateper = new BigDecimal(vatTaxRateVal);
        try {
            // Only VAT available
            /** BigDecimal denominator = vatRateper.divide(hundred, 2, RoundingMode.HALF_UP).setScale(2,
                    RoundingMode.HALF_UP); */
            BigDecimal denominator = vatRateper.divide(hundred, 10, RoundingMode.HALF_UP);
            logger.debug("denominator = "+denominator);
            switch (inputVatCalcType) {
            case "Inclusive":
                logger.debug("Inclusive Condition satisfied");
                // Gross Amount / ((1+Input VAT rate%) * Input VAT rate%).
                BigDecimal denominator1 = denominator.add(one);
                logger.debug("denominator1 = "+denominator1);
                formulaVal = grossAmt.divide(denominator1, 2, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
                logger.debug("formulaVal = "+formulaVal);
                BigDecimal formulaVal1 = formulaVal.multiply(denominator).setScale(2, RoundingMode.HALF_UP);
                logger.debug("formulaVal1 = "+formulaVal1);
                formulaVal = formulaVal1;
                logger.debug("formulaVal = "+formulaVal);
                break;

            case "Exclusive":
                logger.debug("Exclusive Condition satisfied");
                // Trans amount * Input Vat %
                formulaVal = grossAmt.multiply(denominator).setScale(2, RoundingMode.HALF_UP);
                logger.debug("formulaVal = "+formulaVal);
                break;
            default:

            }
        } catch (Exception e) {
            //
        }
        return formulaVal.abs();
    }

}