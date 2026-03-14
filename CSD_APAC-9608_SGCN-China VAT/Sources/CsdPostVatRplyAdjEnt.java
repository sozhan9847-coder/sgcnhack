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
import com.temenos.t24.api.records.ebcsdpostadjtaxdetsupd.EbCsdPostAdjTaxDetsUpdRecord;
import com.temenos.t24.api.records.ebcsdstoreplcategparam.EbCsdStorePlCategParamRecord;
import com.temenos.t24.api.records.ebcsdtaxdetsparam.EbCsdTaxDetsParamRecord;
import com.temenos.t24.api.records.ebcsdvatwhtbestfitupd.EbCsdVatWhtBestFitUpdRecord;
import com.temenos.t24.api.records.ebcsdvatwhtrateparam.EbCsdVatWhtRateParamRecord;
import com.temenos.t24.api.records.tax.TaxRecord;
import com.temenos.t24.api.records.taxtypecondition.TaxTypeConditionRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebcsdacctentdetails.EbCsdAcctEntDetailsTable;
import com.temenos.t24.api.tables.ebcsdacctentdetailseod.EbCsdAcctEntDetailsEodTable;
import com.temenos.t24.api.tables.ebcsdpostadjtaxdetsupd.EbCsdPostAdjTaxDetsUpdTable;

/**
 * @author v.manoj
 * AttachedTo : ACCOUNTS-ADJUST.CAP-SCHEDULE
 *              ACCOUNTS-ADJUST.DUE-SCHEDULE
 * AttachedAs : Post Routine in Activity API
 * Description : Routine to calculate the input VAT for Adjusted Credit Interest Amount
 *               and raising Input VAT+WHT accounting entries and updating live table
 *               
 * @update v.manoj - TSR-1151911 - China VAT Delivery: Issue with LCY Amount
 * @update Jayaraman - TSR-1162200 - Sub division code logic is updated
 */
public class CsdPostVatRplyAdjEnt extends ActivityLifecycle {
    
    DataAccess dataAccess = new DataAccess();
    Session ySession = new Session();
    Contract yCon = new Contract();
    String vatCalcAmt = "";
    String creditIntAmt = "";
    String whtCalcAmt = "";
    String whtCalcType = "";
    String whtRateValue = "";
    String actEffectiveDte = "";
    int bestFitPos;
    boolean fcyFlag = false;
    boolean bstFitFlag = false;
    boolean intTaxDetsFlg = false;
    String yTaxType = "";
    String yPLType = "";
    String inputVatCalcType = "";
    String payind = "";
    String yCsmBulkNm = "CSMBULK";
    String adjDueActNm = "ADJUST.DUE";
    String adjcapActNm = "ADJUST.CAP";
    String systemId = "AC";
    String systemNm = "SYSTEM";
    
    public static final String YEXPENSE = "Expense";
    public static final String INCLUSIVE = "Inclusive";
    public static final String EXCLUSIVE = "Exclusive";
    
    private static Logger logger = LoggerFactory.getLogger("API");

    public void postCoreTableUpdate(AaAccountDetailsRecord accountDetailRecord,
            AaArrangementActivityRecord arrangementActivityRecord, ArrangementContext arrangementContext,
            AaArrangementRecord arrangementRecord, AaArrangementActivityRecord masterActivityRecord,
            TStructure productPropertyRecord, AaProductCatalogRecord productRecord, TStructure record,
            List<TransactionData> transactionData, List<TStructure> transactionRecord) {

        if (!arrangementContext.getActivityStatus().equals("AUTH")) {
            return;
        }
        
        logger.debug(" ***** Account Adjust Cap Schedule Api Routine called ***** ");
        logger.debug("String ActivityId = "+ arrangementContext.getActivityId());
        String ycurrActivity = arrangementActivityRecord.getActivity().getValue();
        logger.debug("String CurrentActivityId = "+ arrangementActivityRecord.getActivity().getValue());
        String yactivityName = getActivityNm(ycurrActivity);
        logger.debug("String activityName = "+ yactivityName);
        
        dataAccess = new DataAccess(this);
        ySession = new Session(this);
        yCon = new Contract(this);
        
        // Get the ArrangementId from the current Activity
        String yArrId = arrangementActivityRecord.getArrangement().getValue();
        yCon.setContractId(yArrId);
        logger.debug("String arrangementId = "+ yArrId);
        List<String> intPropertyList = yCon.getPropertyIdsForPropertyClass("INTEREST");
        logger.debug("String intPropertyList = "+ intPropertyList);
        String intProperty = intPropertyList.get(0);
        logger.debug("String intProp = "+ intProperty);
        AaPrdDesInterestRecord yPrdIntRecord = new AaPrdDesInterestRecord(yCon.getConditionForProperty(intProperty));
        String intPropertyNme = yPrdIntRecord.getIdComp2().getValue(); // Get the Interest Property Name
        logger.debug("String intPropertyNme = "+ intPropertyNme);
        // Get the calculated WHT amount and received Credit Interest
        getCalcTaxAmount(intPropertyNme, arrangementContext, accountDetailRecord);

        if (!intTaxDetsFlg) {
            return;
        }
        logger.debug("Bill Details satisfied");
        // Get the Account Details
        String acctNumber = getAccountId(arrangementActivityRecord.getArrangement().getValue());
        logger.debug("String acctNumber = "+ acctNumber);
        AccountRecord yAcctRec = getAcctRecord(acctNumber);
        // Get the CustomerId
        String yCustomerId = yAcctRec.getCustomer().getValue();
        logger.debug("String yCustomerId = "+ yCustomerId);
        // Get the Customer Details
        CustomerRecord yCustomerRec = getCustomerRec(yCustomerId);
        // Get the Currency of Deposits/CASA
        String yAccountCcy = arrangementRecord.getCurrency().getValue();
        logger.debug("String yAccountCcy = "+ yAccountCcy);
        // Get the Local Currency
        String yLocalCcy = ySession.getLocalCurrency();
        logger.debug("String yLocalCcy = "+ yLocalCcy);
        // FCY Credit Interest Flag
        fcyFlag = getFcyTransFlag(yAccountCcy, yLocalCcy);
        logger.debug("Fcy Entry"+fcyFlag);
        
        // Get the WHT Rate Configured from the EB.CSD.VAT.WHT.RATE.PARAM
        String vatTaxRateVal = "";
        EbCsdVatWhtRateParamRecord yVatWhtParamRec = new EbCsdVatWhtRateParamRecord(this);
        // get the PLCategory from the
        String plCategory = getPlCategFromAccounting(intPropertyNme);
        logger.debug("String plCategory = "+ plCategory);
        // Check the PL Category is parameterized or not.
        String yPlCategParamRecId = checkPLCategParam(plCategory);
        logger.debug("String yPlCategParamRecId = "+ yPlCategParamRecId);
        if (!yPlCategParamRecId.isBlank()) {
            // Get the Parameterized record for the respective PL Category
            yVatWhtParamRec = getVatWhtParamRecord(yPlCategParamRecId);
            String prodCateg = yAcctRec.getCategory().getValue();
            bestFitPos = checkBestFixPos(prodCateg, yCustomerRec, yPlCategParamRecId);
            vatTaxRateVal = getWhtRateParamVal(yVatWhtParamRec, bestFitPos);
            logger.debug("String taxRateVal = "+ vatTaxRateVal);
        }
        logger.debug("String Cust = "+yCustomerId);
        logger.debug("String bestFitPos = "+bestFitPos);
        vatCalcAmt = calculateInputVat(creditIntAmt, vatTaxRateVal);
        logger.debug("String vatCalcAmt = "+vatCalcAmt);
        // Get the exchange rate for the FCY Credit Interest
        String vatexchangeRte = calExhangeRate(yAccountCcy, fcyFlag, "VAT");
        logger.debug("String exchangeRte = "+ vatexchangeRte);
        // Update Interest Details into EB.CSD.POST.ADJ.TAX.DETS.UPD for Input VAT+WHT
        updVatInterestDets(yAcctRec, vatTaxRateVal, fcyFlag, vatexchangeRte, intPropertyNme, acctNumber);
        
        // Set the flag when the Credited the interest amount
        boolean credBillPymtInd = false;
        if (payind.equalsIgnoreCase("Credit")) {
            credBillPymtInd = true;
        }
        logger.debug("String credBillPymtInd = "+credBillPymtInd);
        
        // Raising Leg 2 Entries into EB.CSD.ACCT.ENT.DETAILS.EOD
        if (yactivityName.equalsIgnoreCase(adjcapActNm)) {
            logger.debug(" VAT leg1 adjust cap condition satisfied ");
            updVatLeg1LcyEntDetsEod(yVatWhtParamRec, yAcctRec, acctNumber, arrangementContext, yLocalCcy, plCategory, arrangementActivityRecord, credBillPymtInd);
        } else if (yactivityName.equalsIgnoreCase(adjDueActNm)) {
            logger.debug(" VAT leg1 adjust due condition satisfied ");
            // Raising Leg 2 Entries into EB.CSD.ACCT.ENT.DETAILS
            vatLeg1EntryDetsUpd(yVatWhtParamRec, yAcctRec, acctNumber, arrangementContext, yLocalCcy, plCategory, credBillPymtInd); 
        }
        
        if (fcyFlag) {
            if (yactivityName.equalsIgnoreCase(adjcapActNm)) {
                logger.debug(" VAT leg2 adjust cap condition satisfied ");
                // Raising Leg 2 Entries into EB.CSD.ACCT.ENT.DETAILS.EOD
                updVatLeg2EntFcyDetsEod(yVatWhtParamRec, yAcctRec, acctNumber, arrangementContext, yLocalCcy, plCategory, arrangementActivityRecord, vatexchangeRte, credBillPymtInd);
            } else if (yactivityName.equalsIgnoreCase(adjDueActNm)) {
                logger.debug(" VAT leg2 adjust due condition satisfied ");
                // Raising Leg 2 Entries into EB.CSD.ACCT.ENT.DETAILS
                vatLeg2EntryDetsUpd(yVatWhtParamRec, yAcctRec, acctNumber, vatexchangeRte, arrangementContext, yLocalCcy, credBillPymtInd);
            }
            
            // Raising WHT FCY Accounting Entries when WHT Configured
            if (!whtCalcAmt.isBlank() &&!whtCalcType.isBlank()) {
                logger.debug(" **** WHT Calc Type + FCY Configured in parametrization **** ");
                String whtexchangeRte = calExhangeRate(yAccountCcy, fcyFlag, "WHT");
                logger.debug("String whtexchangeRte = "+ whtexchangeRte);
                if (yactivityName.equalsIgnoreCase(adjcapActNm)) {
                    logger.debug(" WHT leg2 adjust cap condition satisfied ");
                    // Raising Leg 2 Entries into EB.CSD.ACCT.ENT.DETAILS.EOD
                    updWhtLeg2FcyEntDetsEod(yVatWhtParamRec, yAcctRec, acctNumber, whtexchangeRte, arrangementContext, arrangementActivityRecord, credBillPymtInd);
                } else if (yactivityName.equalsIgnoreCase(adjDueActNm)) {
                    logger.debug(" WHT leg2 adjust due condition satisfied ");
                    // Raising Leg 2 Entries into EB.CSD.ACCT.ENT.DETAILS
                    updWhtLeg2EntriesDets(yVatWhtParamRec, yAcctRec, acctNumber, whtexchangeRte, arrangementContext);
                }
            }
        }
    
    }

    //
    private void updWhtLeg2FcyEntDetsEod(EbCsdVatWhtRateParamRecord yVatWhtParamRec, AccountRecord yAcctRec,
            String acctNumber, String whtexchangeRte, ArrangementContext arrangementContext,
            AaArrangementActivityRecord arrangementActivityRecord, boolean credBillPymtInd) {
        /** String lastWorkingDay = ySession.getCurrentVariable("!LAST.WORKING.DAY"); */
        String todayDay = ySession.getCurrentVariable("!TODAY");
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
        ydbtLcyL2AmtLcy = getWhtExchgCcyAmt(whtexchangeRte);
        // ydbtLcyL2TransCode = acEntParamRec.getDrTxnCode().getValue();
        ydbtLcyL2TransCode = fetchTransCode(acEntParamRec, credBillPymtInd, "D");
        ydbtLcyL2CustId = yAcctRec.getCustomer().getValue();
        ydbtLcyL2AcctOff = yDebAcctRecObj.getAccountOfficer().getValue();
        ydbtLcyL2ProdCateg = yDebAcctRecObj.getCategory().getValue();
        ydbtLeg2ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        ydbtLcyL2Ccy = yAcctRec.getCurrency().getValue();
        ydbtLcyL2AmtFcy = whtCalcAmt;
        ydbtLcyL2ExchgRte = whtexchangeRte;
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
                .concat(ydbtLcyL2SysId).concat(yDelimit1).concat(ydbtLcyL2BookingDte).concat(yDelimit1).concat(getCurrMarketVal("WHT"))
                .concat(yDelimit1).concat("CSD."+adjcapActNm);
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
        //ycdtLcyL2TransCode = acEntParamRec.getCrTxnCode().getValue();;
        ycdtLcyL2TransCode = fetchTransCode(acEntParamRec, credBillPymtInd, "C");
        ycdtLcyL2CustId = yAcctRec.getCustomer().getValue();
        ycdtLcyL2AcctOff = yCdtAcctRecObj.getAccountOfficer().getValue();
        ycdtLcyL2ProdCateg = yCdtAcctRecObj.getCategory().getValue();
        ycdtLcyL2ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        ycdtLcyL2Ccy = ySession.getLocalCurrency();
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
                .concat(ycdtLcyL2SysId).concat(yDelimit1).concat(ycdtLcyL2BookingDte).concat(yDelimit1).concat(getCurrMarketVal("WHT"))
                .concat(yDelimit1).concat("CSD."+adjcapActNm);
        logger.debug("String Stmt creditLcyL2Entries = "+ creditLcyL2Entries);
        
        String finWhtLeg2AcctEnt = "";
        if (credBillPymtInd) {
            finWhtLeg2AcctEnt = debitLcyL2Entries.concat("$$").concat(creditLcyL2Entries);
        } else {
            finWhtLeg2AcctEnt = creditLcyL2Entries.concat("$$").concat(debitLcyL2Entries);
        }
        logger.debug("String WHT Adjust Cap Leg1 finWhtLeg2AcctEnt = "+ finWhtLeg2AcctEnt);
        
        EbCsdAcctEntDetailsEodRecord acctEntDetsEodVatleg2Rec = new EbCsdAcctEntDetailsEodRecord(this);
        acctEntDetsEodVatleg2Rec.setAccountingEntry(finWhtLeg2AcctEnt);
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
    private void updVatLeg2EntFcyDetsEod(EbCsdVatWhtRateParamRecord yVatWhtParamRec, AccountRecord yAcctRec,
            String acctNumber, ArrangementContext arrangementContext, String yLocalCcy, String plCategory,
            AaArrangementActivityRecord arrangementActivityRecord, String vatexchangeRte, boolean credBillPymtInd) {
        /** String lastWorkingDay = ySession.getCurrentVariable("!LAST.WORKING.DAY"); */
        String todayDay = ySession.getCurrentVariable("!TODAY");
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
        dbtLcyL2AmtLcy = getExchCcyAmt(vatexchangeRte);
        /** dbtLcyL2TransCode = acEntParamRec.getDrTxnCode().getValue(); */
        dbtLcyL2TransCode = fetchTransCode(acEntParamRec, credBillPymtInd, "D");
        dbtLcyL2CustId = yAcctRec.getCustomer().getValue();
        dbtLcyL2AcctOff = yDebAcctRecObj.getAccountOfficer().getValue();
        dbtLcyL2ProdCateg = yDebAcctRecObj.getCategory().getValue();
        dbtLeg2ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        dbtLcyL2Ccy = yAcctRec.getCurrency().getValue();
        dbtLcyL2AmtFcy = vatCalcAmt;
        dbtLcyL2ExchgRte = vatexchangeRte;
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
                .concat(yDelimit1).concat("CSD."+adjcapActNm);
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
        /** cdtLcyL2TransCode = acEntParamRec.getCrTxnCode().getValue(); */
        cdtLcyL2TransCode = fetchTransCode(acEntParamRec, credBillPymtInd, "C");
        cdtLcyL2CustId = yAcctRec.getCustomer().getValue();
        cdtLcyL2AcctOff = yCdtAcctRecObj.getAccountOfficer().getValue();
        cdtLcyL2ProdCateg = yCdtAcctRecObj.getCategory().getValue();
        cdtLcyL2ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        cdtLcyL2Ccy = ySession.getLocalCurrency();
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
                .concat(yDelimit1).concat("CSD."+adjcapActNm);
        logger.debug("String Stmt creditLcyL2Entries = "+ creditLcyL2Entries);
        
        String finalLeg2AcctEnt = "";
        if (credBillPymtInd) {
            finalLeg2AcctEnt = debitLcyL2Entries.concat("$$").concat(creditLcyL2Entries);
        } else {
            finalLeg2AcctEnt = creditLcyL2Entries.concat("$$").concat(debitLcyL2Entries);
        }
        logger.debug("String Input VAT Adjust Cap Leg1 finalLeg2AcctEnt = "+ finalLeg2AcctEnt);
        
        EbCsdAcctEntDetailsEodRecord acctEntDetsEodVatleg2Rec = new EbCsdAcctEntDetailsEodRecord(this);
        acctEntDetsEodVatleg2Rec.setAccountingEntry(finalLeg2AcctEnt);
        logger.debug("String acctEntDetsEodVatleg2Rec = "+ acctEntDetsEodVatleg2Rec);
        // Forming Unique Reference Number followed with account number
        String uniqueIdVatLeg2 = UUID.randomUUID().toString();
        logger.debug("String uniqueIdVatLeg2 = "+ uniqueIdVatLeg2);
        // Write the details into EB.CSD.ACCT.ENT.DETAILS.EOD
        writeAcctEntEodDets(uniqueIdVatLeg2, acctEntDetsEodVatleg2Rec);
        
    }

    //
    private void updVatLeg1LcyEntDetsEod(EbCsdVatWhtRateParamRecord yVatWhtParamRec, AccountRecord yAcctRec,
            String acctNumber, ArrangementContext arrangementContext, String localCcy, String plCategory,
            AaArrangementActivityRecord arrangementActivityRecord, boolean credBillPymtInd) {
        /** String lastWorkingDay = ySession.getCurrentVariable("!LAST.WORKING.DAY"); */
        String todayDay = ySession.getCurrentVariable("!TODAY");
        // get AC.ENTRY.PARAM record
        AcEntryParamRecord acEntParamRec = getAcParamRec("VAT");
        String yDelimit1 = "*";
        // Set the debit Leg
        String dbtLcL1Acct = "";
        String dbtLcL1CompanyCde = "";
        String dbtLcL1AmtLcy = "";
        String dbtLcL1TransCode = "";
        String dbtLcL1PlCateg = "";
        String dbtLcL1CustId = "";
        String dbtLcL1AcctOff = "";
        String dbtLcL1ProdCateg = "";
        String dbtLcL1ValueDate = "";
        String dbtLcL1Ccy = "";
        String dbtLcL1AmtFcy = "";
        String dbtLcL1ExchgRte = "";
        String dbtLcL1PosType = "";
        String dbtLcL1OutRef = "";
        String dbtLcL1ExpoDate = "";
        String dbtLcL1CcyMkt = "";
        String dbtLcL1TransRef = "";
        String dbtLcL1SysId = "";
        String dbtLcL1BookingDte = "";
        
        if (yTaxType.equalsIgnoreCase("INPUT")&& !fcyFlag) {
            dbtLcL1Acct = getvatPayAcct(yVatWhtParamRec.getInputVatTempAccount().getValue(), localCcy);
            dbtLcL1Ccy = localCcy;
            dbtLcL1AmtLcy = vatCalcAmt;
        }else {
            dbtLcL1Acct = getvatPayAcct(yVatWhtParamRec.getInputVatTempAccount().getValue(), yAcctRec.getCurrency().getValue());
            dbtLcL1Ccy = yAcctRec.getCurrency().getValue();
            dbtLcL1AmtFcy = vatCalcAmt;
            dbtLcL1ExchgRte = getDefExchgRate(yAcctRec.getCurrency().getValue());
            dbtLcL1AmtLcy = getCalcAmtLcy(dbtLcL1AmtFcy, dbtLcL1ExchgRte);
        }
        
        AccountRecord yDebAcctRecObj = getLegAcctRec(dbtLcL1PlCateg, dbtLcL1Acct, yAcctRec);
        dbtLcL1CompanyCde = arrangementActivityRecord.getCoCode(); 
        /** dbtLcL1TransCode = acEntParamRec.getDrTxnCode().getValue(); */
        dbtLcL1TransCode = fetchTransCode(acEntParamRec, credBillPymtInd, "D");
        dbtLcL1CustId = yAcctRec.getCustomer().getValue();
        dbtLcL1AcctOff = yDebAcctRecObj.getAccountOfficer().getValue();
        dbtLcL1ProdCateg = yDebAcctRecObj.getCategory().getValue();
        dbtLcL1ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        dbtLcL1PosType = yDebAcctRecObj.getPositionType().getValue();
        dbtLcL1OutRef = acctNumber;
        dbtLcL1ExpoDate = todayDay;
        dbtLcL1CcyMkt = yDebAcctRecObj.getCurrencyMarket().getValue();
        dbtLcL1TransRef = arrangementContext.getTransactionReference();
        dbtLcL1SysId = systemId;
        dbtLcL1BookingDte = todayDay;
        
        // Forming DebitLeg Message
        String debitLcyL1Entries = dbtLcL1Acct.concat(yDelimit1).concat(dbtLcL1CompanyCde).concat(yDelimit1)
                .concat(dbtLcL1AmtLcy).concat(yDelimit1).concat(dbtLcL1TransCode).concat(yDelimit1).concat(dbtLcL1PlCateg)
                .concat(yDelimit1).concat(dbtLcL1CustId).concat(yDelimit1).concat(dbtLcL1AcctOff).concat(yDelimit1)
                .concat(dbtLcL1ProdCateg).concat(yDelimit1).concat(dbtLcL1ValueDate).concat(yDelimit1).concat(dbtLcL1Ccy)
                .concat(yDelimit1).concat(dbtLcL1AmtFcy).concat(yDelimit1).concat(dbtLcL1ExchgRte).concat(yDelimit1)
                .concat(dbtLcL1PosType).concat(yDelimit1).concat(dbtLcL1OutRef).concat(yDelimit1).concat(dbtLcL1ExpoDate)
                .concat(yDelimit1).concat(dbtLcL1CcyMkt).concat(yDelimit1).concat(dbtLcL1TransRef).concat(yDelimit1)
                .concat(dbtLcL1SysId).concat(yDelimit1).concat(dbtLcL1BookingDte).concat(yDelimit1).concat("CSD."+adjcapActNm);
        logger.debug("String Stmt debitLcyL1Entries = "+ debitLcyL1Entries);
        
        // Set the CreditLeg for the AcctEntries1
        String cdtLcL1Acct = "";
        String cdtLcL1CompanyCode = "";
        String cdtLcL1AmtLcy = "";
        String cdtLcL1TransCode = "";
        String cdtLcL1PlCateg = "";
        String cdtLcL1CustId = "";
        String cdtLcL1AcctOff = "";
        String cdtLcL1ProdCateg = "";
        String cdtLcL1ValueDate = "";
        String cdtLcL1Ccy = "";
        String cdtLcL1AmtFcy = "";
        String cdtLcL1ExchgRte = "";
        String cdtLcL1PosType = "";
        String cdtLcL1OutRef = "";
        String cdtLcL1ExpoDate = "";
        String cdtLcL1CcyMkt = "";
        String cdtLcL1TransRef = "";
        String cdtLcL1SysId = "";
        String cdtLcL1BookingDte = "";
        
        if (yTaxType.equalsIgnoreCase("INPUT")) {
            if (inputVatCalcType.equalsIgnoreCase("Inclusive") && !fcyFlag) {
                cdtLcL1PlCateg = plCategory;
                cdtLcL1Ccy = localCcy;
                cdtLcL1AmtLcy = dbtLcL1AmtLcy;
            } else if (inputVatCalcType.equalsIgnoreCase("Exclusive") || fcyFlag) {
                cdtLcL1Acct = getvatPayAcct(yVatWhtParamRec.getVatPayableAccount().getValue(), dbtLcL1Ccy);
                cdtLcL1Ccy = dbtLcL1Ccy;
                if (fcyFlag) {
                    cdtLcL1AmtFcy = dbtLcL1AmtFcy;
                    cdtLcL1ExchgRte = dbtLcL1ExchgRte;
                    cdtLcL1AmtLcy = dbtLcL1AmtLcy;
                }
            }
        }
        
        // Get the Account Record.
        AccountRecord yCdtAcctRecObj = getLegAcctRec(cdtLcL1PlCateg, cdtLcL1Acct, yAcctRec);
        cdtLcL1CompanyCode = arrangementActivityRecord.getCoCode();
        /** cdtLcL1TransCode = acEntParamRec.getCrTxnCode().getValue(); */
        cdtLcL1TransCode = fetchTransCode(acEntParamRec, credBillPymtInd, "C");
        cdtLcL1CustId = yAcctRec.getCustomer().getValue();
        cdtLcL1AcctOff = yCdtAcctRecObj.getAccountOfficer().getValue();
        cdtLcL1ProdCateg = yCdtAcctRecObj.getCategory().getValue();
        cdtLcL1ValueDate = arrangementActivityRecord.getEffectiveDate().getValue();
        cdtLcL1PosType = yCdtAcctRecObj.getPositionType().getValue();
        cdtLcL1OutRef = acctNumber;
        cdtLcL1ExpoDate = todayDay;
        cdtLcL1CcyMkt = yCdtAcctRecObj.getCurrencyMarket().getValue();
        cdtLcL1TransRef = arrangementContext.getTransactionReference();
        cdtLcL1SysId = systemId;
        cdtLcL1BookingDte = todayDay;
        
        // Forming CreditLeg Message
        String creditLcyL1Entries = cdtLcL1Acct.concat(yDelimit1).concat(cdtLcL1CompanyCode).concat(yDelimit1)
                .concat(cdtLcL1AmtLcy).concat(yDelimit1).concat(cdtLcL1TransCode).concat(yDelimit1).concat(cdtLcL1PlCateg)
                .concat(yDelimit1).concat(cdtLcL1CustId).concat(yDelimit1).concat(cdtLcL1AcctOff).concat(yDelimit1)
                .concat(cdtLcL1ProdCateg).concat(yDelimit1).concat(cdtLcL1ValueDate).concat(yDelimit1).concat(cdtLcL1Ccy)
                .concat(yDelimit1).concat(cdtLcL1AmtFcy).concat(yDelimit1).concat(cdtLcL1ExchgRte).concat(yDelimit1)
                .concat(cdtLcL1PosType).concat(yDelimit1).concat(cdtLcL1OutRef).concat(yDelimit1).concat(cdtLcL1ExpoDate)
                .concat(yDelimit1).concat(cdtLcL1CcyMkt).concat(yDelimit1).concat(cdtLcL1TransRef).concat(yDelimit1)
                .concat(cdtLcL1SysId).concat(yDelimit1).concat(cdtLcL1BookingDte).concat(yDelimit1).concat("CSD."+adjcapActNm);
        logger.debug("String Stmt creditLcyL1Entries = "+ creditLcyL1Entries);
        
        String finalAcctEnt = "";
        if (credBillPymtInd) {
            finalAcctEnt = debitLcyL1Entries.concat("$$").concat(creditLcyL1Entries);
        } else {
            finalAcctEnt = creditLcyL1Entries.concat("$$").concat(debitLcyL1Entries);
        }
        logger.debug("String Input VAT Adjust Cap Leg1 finalAcctEnt = "+ finalAcctEnt);
        
        EbCsdAcctEntDetailsEodRecord acctEntDetsEodVatleg1Rec = new EbCsdAcctEntDetailsEodRecord(this);
        acctEntDetsEodVatleg1Rec.setAccountingEntry(finalAcctEnt);
        logger.debug("String acctEntDetsEodVatleg1Rec = "+ acctEntDetsEodVatleg1Rec);
        // Forming Unique Reference Number followed with account number
        String uniqueIdVatLeg1 = UUID.randomUUID().toString();
        logger.debug("String uniqueIdVatLeg1 = "+ uniqueIdVatLeg1);
        // Write the details into EB.CSD.ACCT.ENT.DETAILS.EOD
        writeAcctEntEodDets(uniqueIdVatLeg1, acctEntDetsEodVatleg1Rec);
        
    }

    //
    private void writeAcctEntEodDets(String uniqueId, EbCsdAcctEntDetailsEodRecord acctEntDetsEodVatRecEod) {
        try {
            EbCsdAcctEntDetailsEodTable acctEntDetsTableObj = new EbCsdAcctEntDetailsEodTable(this);
            acctEntDetsTableObj.write(uniqueId, acctEntDetsEodVatRecEod);
            logger.debug("String acctEntDetsTableObj = "+ acctEntDetsTableObj);
        } catch (Exception e) {
            logger.debug("Error Message at new Table write = "+ e.toString());
        }
    }

    //
    private AcEntryParamRecord getAcParamRec(String taxTypeIdent) {
        AcEntryParamRecord acEntParRec = new AcEntryParamRecord();
        try {
            EbCsdTaxDetsParamRecord taxDetsPrmRec = new EbCsdTaxDetsParamRecord(dataAccess.getRecord(
                    ySession.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.TAX.DETS.PARAM", "", systemNm));
            logger.debug(" String taxTypeIdent = "+taxTypeIdent);
            String paramId = "";
            if (taxTypeIdent.equals("WHT")) {
                logger.debug(" WHT Ac Entry Param condition satisfied ");
                paramId = taxDetsPrmRec.getWhtEntryParam().getValue();
            } else if (taxTypeIdent.equals("VAT")) {
                logger.debug(" VAT Ac Entry Param condition satisfied ");
                paramId = taxDetsPrmRec.getVatEntryParam().getValue();
            }
            acEntParRec = new AcEntryParamRecord(dataAccess.getRecord("AC.ENTRY.PARAM", paramId));
            logger.debug(" String acEntParRec = "+acEntParRec);
        } catch (Exception e) {
            //
        }
        return acEntParRec;
    }

    //
    private AccountRecord getLegAcctRec(String PlCategory, String accountNo, AccountRecord yAcctRec) {
        if (!PlCategory.isBlank()) {
            return yAcctRec;
        } else {
            return new AccountRecord(
                    dataAccess.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "ACCOUNT", "", accountNo));
        }
    }

    //
    private String getCalcAmtLcy(String dbtLcyL1AmtFcy, String dbtLcyL1ExchgRte) {
        BigDecimal yCalAmountLcy = BigDecimal.ZERO;
        try {
            yCalAmountLcy = new BigDecimal(dbtLcyL1AmtFcy).multiply(
                    new BigDecimal(dbtLcyL1ExchgRte)).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            // 
        }
        return yCalAmountLcy.toString();
    }

    //
    private String getDefExchgRate(String currency) {
        String yDefMidRate = "";
        try {
            CurrencyRecord ccyRecObj = new CurrencyRecord(dataAccess.getRecord("CURRENCY", currency));
            for (CurrencyMarketClass ccyMkt : ccyRecObj.getCurrencyMarket()) {
                if (ccyMkt.getCurrencyMarket().getValue().equals("1")) {
                    yDefMidRate = ccyMkt.getMidRevalRate().getValue();
                    break;
                }
            }
        } catch (Exception e) {
            // 
        }
        return yDefMidRate;
    }

    //
    private String getActivityNm(String ycurrActivity) {
        try {
            return ycurrActivity.split("-")[1];
        } catch (Exception e) {
            return "";
        }
    }

    //
    private void updWhtLeg2EntriesDets(EbCsdVatWhtRateParamRecord yVatWhtParamRec, AccountRecord yAcctRec,
            String acctNumber, String whtexchangeRte, ArrangementContext arrangementContext) {
        
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
        
        if (payind.equalsIgnoreCase("Debit")) {
            dbtTransCode = ycdTransCde;
            dbtTraSign = ycdSign;
        } else {
            dbtTransCode = ydbTransCde;
            dbtTraSign = ydbSign;
        }
        
        dbtAcctNo = getvatPayAcct(yVatWhtParamRec.getWhtPayableAccount().getValue(), yAcctRec.getCurrency().getValue());
        /** dbtAmt = getExchCcyAmt(exchangeRte); */
        dbtAmt = getWhtExchgCcyAmt(whtexchangeRte);
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
        
        if (payind.equalsIgnoreCase("Debit")) {
            cdtTransCode = ydbTransCde;
            cdtTraSign = ydbSign;
        } else {
            cdtTransCode = ycdTransCde;
            cdtTraSign = ycdSign;
        }
        
        cdtAccount = getLcyInternalAcct(dbtAcctNo);
        cdtAmount = dbtAmt;
        cdtCcy = ySession.getLocalCurrency();
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
        logger.debug("String whtFcyLet2Entries = "+debitLegEntries.concat("#").concat(creditLegEntries));
        // Forming Unique Reference Number followed with account number
        String uniqueRefId = UUID.randomUUID().toString().concat("$WHT");
        // Write the details into EB.CSD.ACCT.ENT.DETAILS
        writeAcctEntDets(uniqueRefId, acctEntDetsRec);
        
    }

    //
    private String getWhtExchgCcyAmt(String exchangeRte) {
        BigDecimal whtExchngRateCcyAmt = BigDecimal.ZERO;
        try {
            BigDecimal yBdWhtOutputVat = new BigDecimal(whtCalcAmt);
            BigDecimal yBdWhtExchngRate = new BigDecimal(exchangeRte);
            whtExchngRateCcyAmt = yBdWhtOutputVat.multiply(yBdWhtExchngRate).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            //
        }
        return whtExchngRateCcyAmt.toString();
    }

    //
    private void vatLeg1EntryDetsUpd(EbCsdVatWhtRateParamRecord yVatWhtParamRec, AccountRecord yAcctRec,
            String acctNumber, ArrangementContext arrangementContext, String localCcy, String plCategory, boolean credBillPymtInd) {
        String dSign = "D";
        String cSign = "C";
        String dTransCde = "0001";
        String cTransCde = "0002";
        // Set the Process mode based on lcy and fcy
        String yRevDelimit1 = ",";
        String processMode = yCsmBulkNm;
        // Set the debit Leg for the AcctEntries1
        String dbtTransCde = "";
        String dbtPlCategory = "";
        String dbtAccount = "";
        String dbtAmount = "";
        String dbtCurrency = "";
        String dbtTranSign = "";
        String dbtTransReference = "";
        String dbtCustomer = "";
        String dbtAcctOfficer = "";
        String dbtProdCateg = "";
        String dbtOurRef = "";
        
        if (yTaxType.equalsIgnoreCase("INPUT")&& !fcyFlag) {
            dbtAccount = getvatPayAcct(yVatWhtParamRec.getInputVatTempAccount().getValue(), localCcy);
            dbtCurrency = localCcy;
            if (inputVatCalcType.equalsIgnoreCase("Inclusive")) {
                processMode = "CSM";
            }
        } else {
            dbtAccount = getvatPayAcct(yVatWhtParamRec.getInputVatTempAccount().getValue(), yAcctRec.getCurrency().getValue());
            dbtCurrency = yAcctRec.getCurrency().getValue();
        }

        if (credBillPymtInd) {
            dbtTranSign = dSign;
            dbtTransCde = dTransCde;
        } else {
            dbtTranSign = cSign;
            dbtTransCde = cTransCde;
        }
        
        dbtAmount = vatCalcAmt;
        dbtTransReference = arrangementContext.getTransactionReference();
        dbtCustomer = yAcctRec.getCustomer().getValue();
        dbtAcctOfficer = yAcctRec.getAccountOfficer().getValue();
        dbtProdCateg = yAcctRec.getCategory().getValue();
        dbtOurRef = acctNumber;
        // Forming DebitLeg Message
        String debitLegEntries = processMode.concat(yRevDelimit1).concat(dbtTransCde).concat(yRevDelimit1).concat(dbtPlCategory)
                .concat(yRevDelimit1).concat(dbtAccount).concat(yRevDelimit1).concat(dbtAmount).concat(yRevDelimit1)
                .concat(dbtCurrency).concat(yRevDelimit1).concat(dbtTranSign).concat(yRevDelimit1).concat(dbtTransReference)
                .concat(yRevDelimit1).concat(dbtCustomer).concat(yRevDelimit1).concat(dbtAcctOfficer).concat(yRevDelimit1)
                .concat(dbtProdCateg).concat(yRevDelimit1).concat(dbtOurRef);

        // Set the CreditLeg for the AcctEntries1
        String cdtTransCde = "";
        String cdtPlCategory = "";
        String cdtAccount = "";
        String cdtAmount = "";
        String cdtCurrency = "";
        String cdtTranSign = "";
        String cdtTransReference = "";
        String cdtCustomer = "";
        String cdtAcctOfficer = "";
        String cdtProdCateg = "";
        String cdtOurRef = "";
        
        if (yTaxType.equalsIgnoreCase("INPUT")) {
            if (inputVatCalcType.equalsIgnoreCase("Inclusive") && !fcyFlag) {
                cdtPlCategory = plCategory;
                cdtCurrency = localCcy;
                processMode = "CSM";
            } else if (inputVatCalcType.equalsIgnoreCase("Exclusive") || fcyFlag) {
                cdtAccount = getvatPayAcct(yVatWhtParamRec.getVatPayableAccount().getValue(), dbtCurrency);
                cdtCurrency = dbtCurrency;
            } 
        }

        if (credBillPymtInd) {
            cdtTranSign = cSign;
            cdtTransCde = cTransCde;
        } else {
            cdtTranSign = dSign;
            cdtTransCde = dTransCde;
        }
        
        cdtAmount = dbtAmount;
        cdtTransReference = arrangementContext.getTransactionReference();
        cdtCustomer = yAcctRec.getCustomer().getValue();
        cdtAcctOfficer = yAcctRec.getAccountOfficer().getValue();
        cdtProdCateg = yAcctRec.getCategory().getValue();
        cdtOurRef = acctNumber;
        // Forming CreditLeg Message
        String creditLegEntries = processMode.concat(yRevDelimit1).concat(cdtTransCde).concat(yRevDelimit1).concat(cdtPlCategory)
                .concat(yRevDelimit1).concat(cdtAccount).concat(yRevDelimit1).concat(cdtAmount).concat(yRevDelimit1)
                .concat(cdtCurrency).concat(yRevDelimit1).concat(cdtTranSign).concat(yRevDelimit1).concat(cdtTransReference)
                .concat(yRevDelimit1).concat(cdtCustomer).concat(yRevDelimit1).concat(cdtAcctOfficer).concat(yRevDelimit1)
                .concat(cdtProdCateg).concat(yRevDelimit1).concat(cdtOurRef).concat("**Leg1");
        
        EbCsdAcctEntDetailsRecord yAcctEntDetsLeg1Rec = new EbCsdAcctEntDetailsRecord(this);
        yAcctEntDetsLeg1Rec.setAccountingEntry(debitLegEntries.concat("#").concat(creditLegEntries));
        // Forming Unique Reference Number followed with account number
        String uniqueRefId = UUID.randomUUID().toString();
        // Write the details into EB.CSD.ACCT.ENT.DETAILS
        logger.debug("String yAcctEntDetsLeg1Rec = "+yAcctEntDetsLeg1Rec);
        writeAcctEntDets(uniqueRefId, yAcctEntDetsLeg1Rec);
    }

    //
    private String getWhtRateParamVal(EbCsdVatWhtRateParamRecord yVatWhtParamRec, int bestFitPos) {
        String vatRateVal = "";
        if (bstFitFlag) {
            yTaxType = yVatWhtParamRec.getClientCategory(bestFitPos).getTaxType().getValue();
            yPLType = yVatWhtParamRec.getClientCategory(bestFitPos).getPlType().getValue();
            inputVatCalcType = yVatWhtParamRec.getClientCategory(bestFitPos).getInputVatCalculationType().getValue();
            whtCalcType = yVatWhtParamRec.getClientCategory(bestFitPos).getWhtCalculationType().getValue();
            if (yTaxType.equalsIgnoreCase("Input") && yPLType.equalsIgnoreCase("Interest")) {
                // Get the VAT Rate
                String vatRateCond = yVatWhtParamRec.getClientCategory(bestFitPos).getVatRate().getValue();
                String yvatTaxcde = fetchTaxCodeRec(vatRateCond);
                vatRateVal = fetchTaxRate(yvatTaxcde);
                // Get the WHT Rate
                String whtRateCond = yVatWhtParamRec.getClientCategory(bestFitPos).getWhtRate().getValue();
                if (!whtCalcAmt.isBlank() && !whtCalcType.isBlank()
                        && !whtRateCond.isBlank()) {
                    String ywhtTaxcde = fetchTaxCodeRec(whtRateCond);
                    whtRateValue = fetchTaxRate(ywhtTaxcde);
                }
            }
        }
        return vatRateVal;
    }

    //
    private String fetchTaxRate(String ytaxcde) {
        String taxRateVal = "";
        try {
            List<String> ytaxCodeList = dataAccess.selectRecords("", "TAX", "", "WITH @ID LIKE " + ytaxcde + "....");
            TaxRecord ytaxRec = new TaxRecord(dataAccess.getRecord("TAX", getLatestTaxId(ytaxCodeList)));
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
            TaxTypeConditionRecord yTaxTypeCondRec = new TaxTypeConditionRecord(dataAccess.getRecord(
                    ySession.getCompanyRecord().getFinancialMne().getValue(), "TAX.TYPE.CONDITION", "", whtRateCond));
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
                    dataAccess.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.VAT.WHT.BEST.FIT.UPD",
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
                    bstFitFlag = true;
                    break;
                }
            }
            if (!bstFitFlag) {
                yindex = defFitIndex;
                bstFitFlag = true;
            }
        } catch (Exception e) {
            //
        }
        return yindex;
    }

    //
    private EbCsdVatWhtRateParamRecord getVatWhtParamRecord(String yPlCategParamRecId) {
        try {
            return new EbCsdVatWhtRateParamRecord(dataAccess.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(),
                    "EB.CSD.VAT.WHT.RATE.PARAM", "", yPlCategParamRecId));
        } catch (Exception e) {
            return new EbCsdVatWhtRateParamRecord(this);
        }
    }

    //
    private String checkPLCategParam(String plCategory) {
        boolean yParamIdFlag = false;
        String yVatWhtParamId = "";
        EbCsdStorePlCategParamRecord yPlCategParamRec = new EbCsdStorePlCategParamRecord(dataAccess.getRecord(
                ySession.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.STORE.PL.CATEG.PARAM", "", "SYSTEM"));
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
    private void vatLeg2EntryDetsUpd(EbCsdVatWhtRateParamRecord yVatWhtParamRec, AccountRecord yAcctRec,
            String acctNumber, String vatexchangeRte, ArrangementContext arrangementContext, String localCcy, boolean credBillPymtInd) {
        String dtSign = "D";
        String ctSign = "C";
        String dtTransCde = "0001";
        String ctTransCde = "0002";
        // If Local Currency, no need to raise entries
        // Set the Process mode based on lcy and fcy
        String yDelimit1 = ",";
        String processMode = yCsmBulkNm;
        // Set the debit Leg for the AcctEntries2
        String dbtTransCode = "";
        String dbtPlCateg = "";
        String dbtAcctNo = "";
        String dbtAmt = "";
        String dbtCcy = "";
        String dbtTranSgn = "";
        String dbtTransRef = "";
        String dbtCustId = "";
        String dbtAccountOfficer = "";
        String dbtProductCateg = "";
        String dbtOurReference = "";

        dbtAcctNo = getvatPayAcct(yVatWhtParamRec.getVatPayableAccount().getValue(), yAcctRec.getCurrency().getValue());
        dbtAmt = getExchCcyAmt(vatexchangeRte);
        dbtCcy = localCcy;
        dbtTransRef = arrangementContext.getTransactionReference();
        dbtCustId = yAcctRec.getCustomer().getValue();
        dbtAccountOfficer = yAcctRec.getAccountOfficer().getValue();
        dbtProductCateg = yAcctRec.getCategory().getValue();
        dbtOurReference = acctNumber;
        
        if (credBillPymtInd) {
            dbtTranSgn = dtSign;
            dbtTransCode = dtTransCde;
        } else {
            dbtTranSgn = ctSign;
            dbtTransCode = ctTransCde;
        }
        
        // Forming DebitLeg Message
        String debitLegEntries = processMode.concat(yDelimit1).concat(dbtTransCode).concat(yDelimit1).concat(dbtPlCateg)
                .concat(yDelimit1).concat(dbtAcctNo).concat(yDelimit1).concat(dbtAmt).concat(yDelimit1).concat(dbtCcy)
                .concat(yDelimit1).concat(dbtTranSgn).concat(yDelimit1).concat(dbtTransRef).concat(yDelimit1).concat(dbtCustId)
                .concat(yDelimit1).concat(dbtAccountOfficer).concat(yDelimit1).concat(dbtProductCateg).concat(yDelimit1)
                .concat(dbtOurReference);

        // Set the CreditLeg for the AcctEntries2
        String cdtTransCode = "";
        String cdtPlCateg = "";
        String cdtAccount = "";
        String cdtAmount = "";
        String cdtCcy = "";
        String cdtTranSgn = "";
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
        
        if (credBillPymtInd) {
            cdtTranSgn = ctSign;
            cdtTransCode = ctTransCde;
        } else {
            cdtTranSgn = dtSign;
            cdtTransCode = dtTransCde;
        }
        
        // Forming CreditLeg Message
        String creditLegEntries = processMode.concat(yDelimit1).concat(cdtTransCode).concat(yDelimit1).concat(cdtPlCateg)
                .concat(yDelimit1).concat(cdtAccount).concat(yDelimit1).concat(cdtAmount).concat(yDelimit1)
                .concat(cdtCcy).concat(yDelimit1).concat(cdtTranSgn).concat(yDelimit1).concat(cdtTransRef).concat(yDelimit1)
                .concat(cdtCustId).concat(yDelimit1).concat(cdtAccountOfficer).concat(yDelimit1).concat(cdtProductCateg)
                .concat(yDelimit1).concat(cdtOurReference).concat("**Leg2");
        
        EbCsdAcctEntDetailsRecord yAcctEntDetsLeg2Rec = new EbCsdAcctEntDetailsRecord(this);
        yAcctEntDetsLeg2Rec.setAccountingEntry(debitLegEntries.concat("#").concat(creditLegEntries));
        // Forming Unique Reference Number followed with account number
        String uniqueRefId = UUID.randomUUID().toString();
        // Write the details into EB.CSD.ACCT.ENT.DETAILS
        logger.debug("String yAcctEntDetsLeg2Rec = "+yAcctEntDetsLeg2Rec);
        writeAcctEntDets(uniqueRefId, yAcctEntDetsLeg2Rec);
    }

    //
    private String getvatPayAcct(String vatPayableAcct, String localCurrency) {
        String intAcctNum = "";
        for (String yIntAcctNo : dataAccess.getConcatValues("CATEG.INT.ACCT", vatPayableAcct)) {
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
            String ylocalCcy = ySession.getLocalCurrency(); // get the local currency
            String ySubDivCode = ySession.getCompanyRecord().getSubDivisionCode().getValue();
            String yCategId = dbtAcctNo.substring(3, 8); // Get the Category
            String lcyIntAcctId = ylocalCcy.concat(yCategId).concat(ySubDivCode);
            List<String> categIntAcctList = dataAccess.getConcatValues("CATEG.INT.ACCT", yCategId);
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
            EbCsdAcctEntDetailsTable acctEntDetsTab = new EbCsdAcctEntDetailsTable(this);
            acctEntDetsTab.write(uniqueRefId, acctEntDetsRec);
        } catch (Exception e) {
            //
        }
    }

    //
    private void writeDetstoLiveTable(EbCsdPostAdjTaxDetsUpdRecord yPostAdjDetsRec, String acctNumber) {
        try {
            EbCsdPostAdjTaxDetsUpdTable yPostAdjDetsTab = new EbCsdPostAdjTaxDetsUpdTable(this);
            yPostAdjDetsTab.write(acctNumber, yPostAdjDetsRec);
        } catch (Exception e) {
            //
        }
    }

    //
    public void updVatInterestDets(AccountRecord yAcctRec,
            String taxRateVal, boolean yFcyFlag, String vatexchangeRte, String intPropName, String acctNumber) {
        EbCsdPostAdjTaxDetsUpdRecord yPostAdjDetsRec =  new EbCsdPostAdjTaxDetsUpdRecord(this);
        yPostAdjDetsRec.setInterestDetail(intPropName);
        yPostAdjDetsRec.setInterestTransactionType(YEXPENSE);
        yPostAdjDetsRec.setInterestCurrency(yAcctRec.getCurrency().getValue());
        yPostAdjDetsRec.setInputVatRateForInterest(taxRateVal);
        setVatExchgRate(yFcyFlag, yPostAdjDetsRec, vatexchangeRte);
        if (!whtCalcType.isBlank() && !whtCalcAmt.isBlank()) {
            yPostAdjDetsRec.setWhtRateForInterest(whtRateValue);
        }
        updPeriodStartDte(yPostAdjDetsRec, intPropName, yAcctRec);
        yPostAdjDetsRec.setTotalIntAmount(creditIntAmt);
        // Update the Interest Transaction Type
        updateVatAmt(yFcyFlag, yPostAdjDetsRec, vatexchangeRte);
        logger.debug("String yPostAdjDetsRec = "+ yPostAdjDetsRec);
        // Write the adjust activity into EB.CSD.POST.ADJ.TAX.DETS.UPD
        writeDetstoLiveTable(yPostAdjDetsRec, acctNumber);
    }

    //
    private EbCsdPostAdjTaxDetsUpdRecord updateVatAmt(boolean yFcyFlag, EbCsdPostAdjTaxDetsUpdRecord yPostAdjDetsRec, String yExcRate) {
        if (!yFcyFlag) {
            yPostAdjDetsRec.setTotInputValLcyAmtForInt(vatCalcAmt);
        } else {
            yPostAdjDetsRec.setTotInputValLcyAmtForInt(getExchCcyAmt(yExcRate));
            yPostAdjDetsRec.setTotInputVatFcyAmtForInt(vatCalcAmt);
            /** yPostAdjDetsRec.setTotInputValLcyAmtForInt(vatCalcAmt);
            yPostAdjDetsRec.setTotInputVatFcyAmtForInt(getExchCcyAmt(yExcRate)); */
        }
        if (!whtCalcType.isBlank() && !whtCalcAmt.isBlank()) {
            setWhtAmount(yFcyFlag, yPostAdjDetsRec, yExcRate);
        }
        return yPostAdjDetsRec;
    }

    //
    private EbCsdPostAdjTaxDetsUpdRecord setWhtAmount(boolean yFcyFlag, EbCsdPostAdjTaxDetsUpdRecord yPostAdjDetsRec, String yExcRate) {
        if (!yFcyFlag) {
            yPostAdjDetsRec.setTotWhtLcyAmtForInt(whtCalcAmt);
        } else {
            yPostAdjDetsRec.setTotWhtLcyAmtForInt(getWhtExchgCcyAmt(yExcRate));
            yPostAdjDetsRec.setTotWhtFcyAmtForInt(whtCalcAmt);
            /** yPostAdjDetsRec.setTotWhtLcyAmtForInt(whtCalcAmt);
            yPostAdjDetsRec.setTotWhtFcyAmtForInt(getWhtExchgCcyAmt(yExcRate)); */
            
        }
        return yPostAdjDetsRec;
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
    private EbCsdPostAdjTaxDetsUpdRecord setVatExchgRate(boolean yFcyFlag, EbCsdPostAdjTaxDetsUpdRecord yPostAdjDetsRec, String yExcRate) {
        if (yFcyFlag) {
            yPostAdjDetsRec.setVatExchangeRateForInterest(yExcRate);
            if (!whtCalcType.isBlank() && !whtCalcAmt.isBlank()) {
                yPostAdjDetsRec.setWhtExchangeRateForInterest(yExcRate);
            }
        }
        return yPostAdjDetsRec; 
    }

    //
    private EbCsdPostAdjTaxDetsUpdRecord updPeriodStartDte(EbCsdPostAdjTaxDetsUpdRecord yPostAdjDetsRec, String intPropName,
            AccountRecord yAcctRec) {
        // Update the total interest details from the AA.INTEREST.ACCRUAL
        String yIntAccId = yAcctRec.getArrangementId().getValue().concat("-").concat(intPropName);
        AaInterestAccrualsRecord yIntAccRec = new AaInterestAccrualsRecord(dataAccess.getRecord(
                ySession.getCompanyRecord().getFinancialMne().getValue(), "AA.INTEREST.ACCRUALS", "", yIntAccId));
        for (com.temenos.t24.api.records.aainterestaccruals.PeriodStartClass yIntPerAcc : yIntAccRec.getPeriodStart()) {
            if (yIntPerAcc.getPeriodEnd().getValue().equals(actEffectiveDte)) {
                yPostAdjDetsRec.setPeriodStart(yIntPerAcc.getPeriodStart().getValue());
                yPostAdjDetsRec.setPeriodEnd(yIntPerAcc.getPeriodEnd().getValue());
                break;
            }
        }
        return yPostAdjDetsRec;
    }

    //
    private CustomerRecord getCustomerRec(String yCustomerId) {
        try {
            return new CustomerRecord(
                    dataAccess.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "CUSTOMER", "", yCustomerId));
        } catch (Exception e) {
            return new CustomerRecord(this);
        }
    }

    //
    private AccountRecord getAcctRecord(String accountNumber) {
        try {
            return new AccountRecord(
                    dataAccess.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "ACCOUNT", "", accountNumber));
        } catch (Exception e) {
            return new AccountRecord(this);
        }
    }

    //
    private String getAccountId(String yArrId) {
        String yAcctNumber = "";
        try {
            AaArrangementRecord yArrRec = new AaArrangementRecord(dataAccess.getRecord("AA.ARRANGEMENT", yArrId));
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
            CurrencyRecord yCurrency = new CurrencyRecord(dataAccess.getRecord("CURRENCY", acctCurr));
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
            EbCsdTaxDetsParamRecord ytaxDetsParamRec = new EbCsdTaxDetsParamRecord(dataAccess.getRecord(
                    ySession.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.TAX.DETS.PARAM", "", "SYSTEM"));
            String acparamId = "";
            if (taxIden.equals("WHT")) {
                acparamId = ytaxDetsParamRec.getWhtEntryParam().getValue();
            } else if (taxIden.equals("VAT")) {
                acparamId = ytaxDetsParamRec.getVatEntryParam().getValue();
            }
            AcEntryParamRecord yEntryParamRec = new AcEntryParamRecord(
                        dataAccess.getRecord("AC.ENTRY.PARAM", acparamId));
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
                    if (tBillId.getActivityRef().getValue().equals(currAAAId) && !intTaxDetsFlg) {
                        String yBillIdVal = tBillId.getBillId().getValue();
                        logger.debug("String yBillIdVal ="+ yBillIdVal);
                        // Check Credit Interest and Tax Details available in AA.BILL.DETAILS
                        checkIntAndTaxDets(intPropName, yBillIdVal);
                        if (!creditIntAmt.isBlank()) {
                            intTaxDetsFlg = true;
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
            logger.debug("String whttaxPropertyId = "+ taxPropertyId);
            TStructure tTaxStruct = yCon.getConditionForProperty(taxPropertyId.get(0));
            AaPrdDesTaxRecord taxCondRec = new AaPrdDesTaxRecord(tTaxStruct);
            String idcomp2 = taxCondRec.getIdComp2().getValue();
            logger.debug("String whtidcomp2 = "+ idcomp2);
            String taxPropId = intPropName+"-"+idcomp2;
            logger.debug("String whttaxPropId = "+ taxPropId);
            
            AaBillDetailsRecord yBillDetailsRec = new AaBillDetailsRecord(
                    dataAccess.getRecord("AA.BILL.DETAILS", billId));
            payind = yBillDetailsRec.getPaymentIndicator().getValue();
            for (PropertyClass tPropertyId : yBillDetailsRec.getProperty()) {
                if (tPropertyId.getProperty().getValue().equals(taxPropId)) {
                    whtCalcAmt = tPropertyId.getOrPropAmount().getValue();
                    logger.debug("String whtCalcAmt = "+ whtCalcAmt);
                }
                if (tPropertyId.getProperty().getValue().equals(intPropName)) {
                    creditIntAmt = tPropertyId.getOrPropAmount().getValue();
                    logger.debug("String creditIntAmt ="+ creditIntAmt);
                }
            }
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
            vatAmt = calCombinationInpVat(grossAmt, vatTaxRateVal);
            logger.debug("String vatAmt ="+vatAmt);
            returnResult = vatAmt.abs().toString();
        }
        return returnResult;
    }

    //
    private BigDecimal calCombinationInpVat(BigDecimal grossAmt, String vatTaxRateVal) {
        VatWhtResult resultAmt = null;
        BigDecimal vatAmt = BigDecimal.ZERO;
        try {
            BigDecimal vatRate = new BigDecimal(vatTaxRateVal);
            BigDecimal whtRate = new BigDecimal(whtRateValue);
            if (inputVatCalcType.equals(INCLUSIVE) && whtCalcType.equals(EXCLUSIVE)) {
                logger.debug(" Input + WHT Inclusive Condition true ");
                resultAmt = CsdWithStandTaxCalc.calculateVatWhtIncExc(grossAmt, vatRate, whtRate);
                vatAmt = resultAmt.vatAmount;
                logger.debug("String vatAmt ="+vatAmt);
            }
            if (inputVatCalcType.equals(INCLUSIVE) && whtCalcType.equals(INCLUSIVE)) {
                logger.debug(" Input Inclusive + WHT Exclusive Condition true ");
                resultAmt = CsdWithStandTaxCalc.calculateVatWhtInclusive(grossAmt, vatRate, whtRate); //
                vatAmt = resultAmt.vatAmount;
                logger.debug("String vatAmt ="+vatAmt);
            }
            if (inputVatCalcType.equals(EXCLUSIVE) && whtCalcType.equals(INCLUSIVE)) {
                logger.debug(" Input Exclusive + WHT Inclusive Condition true ");
                resultAmt = CsdWithStandTaxCalc.calculateVatWhtExcInc(grossAmt, vatRate, whtRate); //
                vatAmt = resultAmt.vatAmount;
                logger.debug("String vatAmt ="+vatAmt);
            }
            if (inputVatCalcType.equals(EXCLUSIVE) && whtCalcType.equals(EXCLUSIVE)) {
                logger.debug(" Input + WHT Exclusive Condition true ");
                resultAmt = CsdWithStandTaxCalc.calculateVatWhtExclusive(grossAmt, vatRate, whtRate);
                vatAmt = resultAmt.vatAmount;
                logger.debug("String vatAmt ="+vatAmt);
            }
        } catch (Exception e) {
            //
        }
        return vatAmt;
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
                logger.debug("formulaVal = "+formulaVal.abs());
                break;

            case "Exclusive":
                logger.debug("Exclusive Condition satisfied");
                // Trans amount * Input Vat %
                formulaVal = grossAmt.multiply(denominator).setScale(2, RoundingMode.HALF_UP);
                logger.debug("formulaVal = "+formulaVal.abs());
                break;
            default:

            }
        } catch (Exception e) {
            //
        }
        return formulaVal.abs();
    }

}