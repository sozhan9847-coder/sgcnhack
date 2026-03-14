package com.temenos.csd.vat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.temenos.t24.api.arrangement.accounting.Contract;
import com.temenos.t24.api.hook.accounting.AccountingEntry;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaarrangementactivity.AaArrangementActivityRecord;
import com.temenos.t24.api.records.aainterestaccruals.AaInterestAccrualsRecord;
import com.temenos.t24.api.records.account.AccountRecord;
import com.temenos.t24.api.records.acentryparam.AcEntryParamRecord;
import com.temenos.t24.api.records.currency.CurrencyMarketClass;
import com.temenos.t24.api.records.currency.CurrencyRecord;
import com.temenos.t24.api.records.customer.CustomerRecord;
import com.temenos.t24.api.records.ebcsdacctentdetails.EbCsdAcctEntDetailsRecord;
import com.temenos.t24.api.records.ebcsdacctentdetailseod.EbCsdAcctEntDetailsEodRecord;
import com.temenos.t24.api.records.ebcsdacctnodetsupd.EbCsdAcctNoDetsUpdRecord;
import com.temenos.t24.api.records.ebcsdstoreplcategparam.EbCsdStorePlCategParamRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.ClientBrdIdClass;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.InterestDetailClass;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.PeriodStartClass;
import com.temenos.t24.api.records.ebcsdtaxdetsparam.EbCsdTaxDetsParamRecord;
import com.temenos.t24.api.records.ebcsdvatwhtbestfitupd.EbCsdVatWhtBestFitUpdRecord;
import com.temenos.t24.api.records.ebcsdvatwhtrateparam.EbCsdVatWhtRateParamRecord;
import com.temenos.t24.api.records.fundstransfer.FundsTransferRecord;
import com.temenos.t24.api.records.paymentorder.PaymentOrderRecord;
import com.temenos.t24.api.records.porpostingandconfirmation.ChargePartyIndicatorClass;
import com.temenos.t24.api.records.porpostingandconfirmation.PorPostingAndConfirmationRecord;
import com.temenos.t24.api.records.porsupplementaryinfo.PorSupplementaryInfoRecord;
import com.temenos.t24.api.records.portransaction.PorTransactionRecord;
import com.temenos.t24.api.records.pporderentry.CreditchargecomponentClass;
import com.temenos.t24.api.records.pporderentry.DebitchargecomponentClass;
import com.temenos.t24.api.records.pporderentry.PpOrderEntryRecord;
import com.temenos.t24.api.records.stmtentry.StmtEntryRecord;
import com.temenos.t24.api.records.tax.TaxRecord;
import com.temenos.t24.api.records.taxtypecondition.TaxTypeConditionRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebcsdacctentdetails.EbCsdAcctEntDetailsTable;
import com.temenos.t24.api.tables.ebcsdacctentdetailseod.EbCsdAcctEntDetailsEodTable;
import com.temenos.t24.api.tables.ebcsdacctnodetsupd.EbCsdAcctNoDetsUpdTable;
import com.temenos.t24.api.tables.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdTable;
import com.temenos.csd.vat.CsdWithStandTaxCalc.VatWhtResult;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.api.TField;

/**
 * @author v.manoj EB.API>CSD.VAT.WHT.CALCULATION.AMT Attached To:
 *         ACCOUNT.PARAMETER>SYSTEM Attached As: Accounting Subroutine
 *         Description: Routine to calculate the VAT and WHT amount based on the
 *         rate defined in the parameter table and update the details in the
 *         live table.
 * @update s.aananthalakshmi INPUT VAT calculation
 * @update v.manoj - TSR-1151911 - China VAT Delivery: Issue with LCY Amount
 * @update Jayaraman - TSR-1162200 - Sub division code logic is updated
 * @update v.manoj - TSR-1167609 - SG China VAT: Fee currency missing in VAT Table entry
 */
public class CsdVatwhtCalculationAmt extends AccountingEntry {
    
    Session ySession = new Session();
    DataAccess da = new DataAccess();
    boolean yLiveRecFlag = false;
    boolean yBestFitFlag = false;
    String paymentIden = "PP";
    String arrIden = "AAAA";
    String accIden = "AC";
    String ftIden = "FT";
    String plCategEntMne = "PL";
    String yIncome = "Income";
    String yExpense = "Expense";
    String yBulkNm = "CSMBULK";
    String yDTransCode = "0001";
    String yCTransCode = "0002";
    String companyMne = "";
    String ySystemId = "SYSTEM";
    String vatCalcType = "";
    String whtCalcType = "";
    String taxType = "";
    public static final String INPUT = "Input";
    public static final String OUTPUT = "Output";
    public static final String INTEREST = "Interest";
    public static final String FEE = "Fee";
    public static final String INCLUSIVE = "Inclusive";
    public static final String EXCLUSIVE = "Exclusive";
    public static final String CSD_CUSTOMER_ID = "CSD.CUSTOMER.ID";
    public static final String POR_TRANSACTION = "POR.TRANSACTION";
    String bstFitCustId = "";
    
    private static Logger logger = LoggerFactory.getLogger("API");
    
    boolean inputVatind = false;
    boolean flagFcy = false;
    boolean vatEmpty = false;
    boolean whtEmpty = false;
    boolean whtInd = false;
    
    @Override
    public void exportEntries(String entryType, String actionType, String statementId,
            StmtEntryRecord accountingEntry) {
        
        logger.debug(" ***** Account Parameter L3 Routine called ***** ");
        logger.debug("String entryType = "+ entryType);
        logger.debug("String actionType = "+ actionType);
        logger.debug("String statementId = "+ statementId);
        
        ySession = new Session(this);
        da = new DataAccess(this);
        CsdCallRevProcessApi yCallApiObj = new CsdCallRevProcessApi(this);
        List<String> sysIdList = new ArrayList<>();
        sysIdList.add(arrIden);
        sysIdList.add(paymentIden);
        sysIdList.add(accIden);
        sysIdList.add(ftIden);
        
        List<String> yTheirRefList = new ArrayList<>();
        yTheirRefList.add("CSD.CAPITALISE");
        yTheirRefList.add("ADJUST.CAP");
        
        String plCategParamId = "";
        // Proceed when the accounting Entries as CATEG and PL Category is
        // parameterized.
        if (!entryType.equals("CATG")) {
            return;
        } else {
            logger.debug("*** Receiving entry is Categ Entry ***");
            String plCategory = accountingEntry.getPlCategory().getValue();
            logger.debug("String Categ Entry plCategory = "+plCategory);
            // Check the PL Category is parameterized or not.
            plCategParamId = checkPLCategEntry(plCategory);
            if (plCategParamId.isBlank()) {
                return;
            }
        }
        
        logger.debug(" ***** Parametrized Condition crossed ***** ");
        logger.debug("String accountingEntry = "+ accountingEntry);
        logger.debug("String Parametrized plCategParamId = "+ plCategParamId);
        // Get the Account Number
        String acctNumber = getAcctNum(accountingEntry);
        logger.debug("String acctNumber = "+ acctNumber);
        String theirRef = accountingEntry.getTheirReference().getValue();
        // Get the Parameterized record for the respective PL Category
        EbCsdVatWhtRateParamRecord yVatWhtParamRec = getVatWhtParamRec(plCategParamId);
        
        if (!accountingEntry.getReversalMarker().getValue().isBlank()) {
            logger.debug(" ***** Reversal Marker flag Condition satisfied ***** ");
            yCallApiObj.raiseReversalEntry(accountingEntry, acctNumber, yVatWhtParamRec, ySession, da);
            logger.debug(" ***** Reversal Call Api Completed ***** ");
        } else {
            EbCsdTaxDetsParamRecord ytaxDetsParamRec = getTaxDetsParamRecord();
            String vatEntryParamId = ytaxDetsParamRec.getVatEntryParam().getValue();
            String prodCateg = accountingEntry.getProductCategory().getValue();
            String systemId = accountingEntry.getSystemId().getValue();
            logger.debug("String systemId = "+ systemId);
            // Get the Parameterized record for the respective PL Category
            // Get the Account Details
            AccountRecord yAcctRec = getAcctDets(acctNumber);
            // Get the customer details
            CustomerRecord yCustRec = getCustomerDets(accountingEntry);
            // check the account record is already available in Live Table
            EbCsdTaxDetailsUpdRecord yTaxDetsUpdRec = checkLiveTable(acctNumber);
            // Update the common data into the live table record
            updateCommonData(yCustRec, accountingEntry, yAcctRec, yTaxDetsUpdRec);
            logger.debug("String yTaxDetsUpdRec = "+ yTaxDetsUpdRec);
            
            String exchangeRate = "";
            int bestFitIndex = checkBestFixIndex(prodCateg, yCustRec, plCategParamId);
            logger.debug("String bestFitIndex = "+ bestFitIndex);
            if (yBestFitFlag) {
                logger.debug("*** Best Fit Flag Condition Satisfied ***");
                taxType = yVatWhtParamRec.getClientCategory(bestFitIndex).getTaxType().getValue();
                String plType = yVatWhtParamRec.getClientCategory(bestFitIndex).getPlType().getValue();
                String taxTypeCond = yVatWhtParamRec.getClientCategory(bestFitIndex).getVatRate().getValue();
                String taxcde = fetchTaxCode(taxTypeCond);
                String taxRateVal = fetchTaxRate(taxcde);
                logger.debug("String taxRateVal = "+ taxRateVal);
                if (taxType.equalsIgnoreCase(OUTPUT)
                        && !accountingEntry.getTheirReference().getValue().startsWith(vatEntryParamId.substring(0, 3))
                        && sysIdList.contains(systemId)) {
                    String outRevEntFlag = "N";
                    logger.debug("***Output VAT Condition satisfied***");
                    if (plType.equalsIgnoreCase(INTEREST) && 
                            !accountingEntry.getAmountLcy().getValue().startsWith("-")) {
                        logger.debug("***Output VAT Interest Condition satisfied***");
                        updTotalIntDets(yTaxDetsUpdRec, accountingEntry, yAcctRec, yVatWhtParamRec, bestFitIndex,
                                acctNumber);
                    } else if (plType.equalsIgnoreCase(FEE)) {
                        logger.debug("***Output VAT Fee Condition satisfied***");
                        // Calculate the Output VAT amount for the Transaction amount
                        String outputVatCalcAmt = calculateOutputVatAmt(accountingEntry, taxRateVal);
                        logger.debug("String Output VAT Fee outputVatCalcAmt = "+ outputVatCalcAmt);
                        // Calculate then Exchange Rate when the Foreign Transaction Currency is happened
                        exchangeRate = calculateExhangeRate(accountingEntry);logger.debug("String exchangeRate = "+ exchangeRate);
                        if (!accountingEntry.getPlCategory().getValue().isBlank() && 
                                !accountingEntry.getAmountLcy().getValue().startsWith("-")) {
                            logger.debug("*** Amount LCY Positive Condition satisfied ***");
                            // Forming the transaction details for fee
                            String feeTransDets = updateChgDets(yAcctRec, systemId, accountingEntry, outputVatCalcAmt,
                                    exchangeRate, taxRateVal, "NO");
                            logger.debug("String feeTransDets = "+ feeTransDets);
                            // Update the accounting entries details into EB.CSD.ACCT.ENT.DETAILS
                            updAcctEntriesDets(accountingEntry, outputVatCalcAmt, yVatWhtParamRec, exchangeRate, acctNumber,
                                    feeTransDets, plType);
                        } else if (!accountingEntry.getPlCategory().getValue().isBlank() && 
                                accountingEntry.getAmountLcy().getValue().startsWith("-")) {
                            outRevEntFlag = "R";
                            logger.debug(" ***** Fee Manual Reversal Condition satisfied ***** ");
                            // Forming the transaction details for fee
                            String feeTransDets = updateChgDets(yAcctRec, systemId, accountingEntry, outputVatCalcAmt,
                                    exchangeRate, taxRateVal, "YES");
                            logger.debug("String feeTransDets = "+ feeTransDets);
                            // Update the Reversal Contra accounting entries details into EB.CSD.ACCT.ENT.DETAILS
                            updReversalFeeAcctEnt(accountingEntry, outputVatCalcAmt, yVatWhtParamRec, exchangeRate, acctNumber,
                                    feeTransDets, plType);
                        }
                        // Write the Account Number into the live table
                        writeAcctIdDets(acctNumber, outRevEntFlag);
                    }
                } else if (taxType.equalsIgnoreCase(INPUT)
                        && !accountingEntry.getTheirReference().getValue().startsWith(vatEntryParamId.substring(0, 3))
                        && sysIdList.contains(systemId) && !theirRef.equals("CSD.CAPITALISE") && !theirRef.equals("CSD.ADJUST.CAP")) {
                    logger.debug("***Input VAT Condition satisfied***");
                    boolean accrualFlag = false;
                    String softAcctDets = accountingEntry.getSoftAcctngDtls().getValue();
                    logger.debug("String softAcctDets = "+ softAcctDets);
                    String rcDetailId = accountingEntry.getRcDetailId().getValue();
                    logger.debug("String rcDetailId = "+ rcDetailId);
                    if (softAcctDets.contains("ACCOUNTS-INTEREST") 
                            || softAcctDets.contains("DEPOSIT-INTEREST")) {
                        accrualFlag = true;
                    } else if (!accrualFlag && (rcDetailId.contains("ACCOUNTS-INTEREST")
                            || rcDetailId.contains("DEPOSIT-INTEREST"))) {
                        accrualFlag = true;
                    }
                    logger.debug("String accrualFlag = "+ accrualFlag);
                    if (!accrualFlag) {
                        logger.debug("***Input VAT Inner Condition satisfied***");
                        inputVatind = true;
                        processInputVatCalculation(accountingEntry, yVatWhtParamRec, bestFitIndex, yTaxDetsUpdRec, acctNumber,
                                taxRateVal, yAcctRec);
                    }
                }
            }
        }
    }

    //
    private void updReversalFeeAcctEnt(StmtEntryRecord accountingEntry, String outputVatCalcAmt,
            EbCsdVatWhtRateParamRecord yVatWhtParamRec, String exchangeRate, String acctNumber, String feeTransDets,
            String plType) {
        // Set the Process mode based on lcy and fcy
        String yRevDelimit = ",";
        String revProcessMode = "";
        if (accountingEntry.getAmountFcy().getValue().isBlank() || (inputVatind)) {
            revProcessMode = yBulkNm ;
        } else {
            revProcessMode = "CSM";
        }
        // Set the debit Leg for the AcctEntries1
        String ycdtRevPlCategory = "";
        String ycdtRevAccount = "";
        String ycdtRevAmount = "";
        String ycdtRevCurrency = "";
        String ycdtRevTranSign = "C";
        String ycdtRevTransReference = "";
        String ycdtRevCustomer = "";
        String ycdtRevAcctOfficer = "";
        String ycdtRevProdCateg = "";
        String ycdtRevOurRef = "";
        ycdtRevOurRef = accountingEntry.getOurReference().getValue();

        if (taxType.equalsIgnoreCase(INPUT)) {
            ycdtRevAccount = getvatPayAcct(yVatWhtParamRec.getInputVatTempAccount().getValue(), accountingEntry);
        } else if (taxType.equalsIgnoreCase(OUTPUT) || whtInd) {
            ycdtRevPlCategory = accountingEntry.getPlCategory().getValue();
            whtInd = false;
        }

        ycdtRevAmount = getFcyWotConv(outputVatCalcAmt, plType, accountingEntry, exchangeRate);
        ycdtRevCurrency = accountingEntry.getCurrency().getValue();
        ycdtRevTransReference = accountingEntry.getTransReference().getValue();
        ycdtRevCustomer = accountingEntry.getCustomerId().getValue();
        ycdtRevAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        ycdtRevProdCateg = accountingEntry.getProductCategory().getValue();
        // Forming DebitLeg Message
        String ycdtRevLeg1Entries = revProcessMode.concat(yRevDelimit).concat(yCTransCode).concat(yRevDelimit)
                .concat(ycdtRevPlCategory).concat(yRevDelimit).concat(ycdtRevAccount).concat(yRevDelimit).concat(ycdtRevAmount)
                .concat(yRevDelimit).concat(ycdtRevCurrency).concat(yRevDelimit).concat(ycdtRevTranSign).concat(yRevDelimit)
                .concat(ycdtRevTransReference).concat(yRevDelimit).concat(ycdtRevCustomer).concat(yRevDelimit)
                .concat(ycdtRevAcctOfficer).concat(yRevDelimit).concat(ycdtRevProdCateg).concat(yRevDelimit).concat(ycdtRevOurRef);
        
        // Set the CreditLeg for the AcctEntries1
        String ydbtRevPlCategory = "";
        String ydbtRevAccount = "";
        String ydbtRevAmount = "";
        String ydbtRevCurrency = "";
        String ydbtRevTranSign = "D";
        String ydbtRevTransReference = "";
        String ydbtRevCustomer = "";
        String ydbtRevAcctOfficer = "";
        String ydbtRevProdCateg = "";
        String ydbtRevOurRef = "";
        if (taxType.equals(INPUT) && !vatCalcType.equals("") && vatCalcType.equalsIgnoreCase(INCLUSIVE) && !flagFcy) {
            ydbtRevPlCategory = accountingEntry.getPlCategory().getValue();
        } else if (taxType.equals(OUTPUT)
                || (taxType.equals(INPUT) && !vatCalcType.equals("") && vatCalcType.equalsIgnoreCase(EXCLUSIVE))
                || checkIncFcy()) {
            ydbtRevAccount = getvatPayAcct(yVatWhtParamRec.getVatPayableAccount().getValue(), accountingEntry);
        }

        ydbtRevAmount = ycdtRevAmount;
        ydbtRevCurrency = accountingEntry.getCurrency().getValue();
        ydbtRevTransReference = accountingEntry.getTransReference().getValue();
        ydbtRevCustomer = accountingEntry.getCustomerId().getValue();
        ydbtRevAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        ydbtRevProdCateg = accountingEntry.getProductCategory().getValue();
        ydbtRevOurRef = accountingEntry.getOurReference().getValue();
        // Forming CreditLeg Message
        String yDbtRevLeg1Entries = revProcessMode.concat(yRevDelimit).concat(yDTransCode).concat(yRevDelimit)
                .concat(ydbtRevPlCategory).concat(yRevDelimit).concat(ydbtRevAccount).concat(yRevDelimit).concat(ydbtRevAmount)
                .concat(yRevDelimit).concat(ydbtRevCurrency).concat(yRevDelimit).concat(ydbtRevTranSign).concat(yRevDelimit)
                .concat(ydbtRevTransReference).concat(yRevDelimit).concat(ydbtRevCustomer).concat(yRevDelimit)
                .concat(ydbtRevAcctOfficer).concat(yRevDelimit).concat(ydbtRevProdCateg).concat(yRevDelimit).concat(ydbtRevOurRef);
        logger.debug("String Revleg1acctEntries1 = "+yDbtRevLeg1Entries.concat("#").concat(ycdtRevLeg1Entries).concat("**").concat("Leg1"));
        
        EbCsdAcctEntDetailsRecord leg1RevacctEntDetsRec = new EbCsdAcctEntDetailsRecord(this);
        leg1RevacctEntDetsRec.setAccountingEntry(yDbtRevLeg1Entries.concat("#").concat(ycdtRevLeg1Entries).concat("**").concat("Leg1"));
        // Forming Unique Reference Number followed with account number
        String yRevLeg1UniqId = "";
        if (plType.equalsIgnoreCase("Fee")) {
            leg1RevacctEntDetsRec.setTransConcatDets(feeTransDets);
            yRevLeg1UniqId = UUID.randomUUID().toString() + "*" + plType + "*" + acctNumber;
        } else {
            yRevLeg1UniqId = UUID.randomUUID().toString();
        }
        // Write the details into EB.CSD.ACCT.ENT.DETAILS
        writeAcctEntDets(yRevLeg1UniqId, leg1RevacctEntDetsRec);
        // Is for FCY Currency
        if (!accountingEntry.getAmountFcy().getValue().isBlank()) {
            EbCsdAcctEntDetailsRecord leg2RevacctEntDetsRec = new EbCsdAcctEntDetailsRecord(this);
            String acctEntries2 = getRevLeg2AcctEntries(exchangeRate, ydbtRevAmount, ydbtRevAccount, accountingEntry,
                    outputVatCalcAmt, plType);
            logger.debug("String Revleg1acctEntries1 = "+acctEntries2);
            leg2RevacctEntDetsRec.setAccountingEntry(acctEntries2);
            // Forming Unique Reference Number followed with account number
            writeAcctEntDets(UUID.randomUUID().toString(), leg2RevacctEntDetsRec);
        }
        
    }

    //
    private String getRevLeg2AcctEntries(String exchangeRate, String ydbtRevAmount, String ydbtRevAccount,
            StmtEntryRecord accountingEntry, String outputVatCalcAmt, String plType) {
        String yRDelimit2 = ",";
        // Set the debitLeg for the AcctEntries2
        String yCdtRevPlCategory = "";
        String yCdtRevAccount = "";
        String yCdtRevAmount = "";
        String yCdtRevCurrency = "";
        String yCdtRevTranSign = "C";
        String yCdtRevTransReference = "";
        String yCdtRevCustomer = "";
        String yCdtRevAcctOfficer = "";
        String yCdtRevProdCateg = "";
        String yCdtRevOurRef = "";
        yCdtRevAccount = ydbtRevAccount;
        if (plType.equalsIgnoreCase("Fee") || inputVatind) {
            yCdtRevAmount = getExchangeCurrencyAmt(ydbtRevAmount, exchangeRate);
        } else {
            yCdtRevAmount = outputVatCalcAmt;
        }
        yCdtRevCurrency = ySession.getLocalCurrency();
        yCdtRevTransReference = accountingEntry.getTransReference().getValue();
        yCdtRevCustomer = accountingEntry.getCustomerId().getValue();
        yCdtRevAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        yCdtRevProdCateg = accountingEntry.getProductCategory().getValue();
        yCdtRevOurRef = accountingEntry.getOurReference().getValue();

        // Forming DebitLeg Message
        String yCdtRevLeg2Entries = yBulkNm.concat(yRDelimit2).concat(yCTransCode).concat(yRDelimit2).concat(yCdtRevPlCategory)
                .concat(yRDelimit2).concat(yCdtRevAccount).concat(yRDelimit2).concat(yCdtRevAmount).concat(yRDelimit2)
                .concat(yCdtRevCurrency).concat(yRDelimit2).concat(yCdtRevTranSign).concat(yRDelimit2)
                .concat(yCdtRevTransReference).concat(yRDelimit2).concat(yCdtRevCustomer).concat(yRDelimit2)
                .concat(yCdtRevAcctOfficer).concat(yRDelimit2).concat(yCdtRevProdCateg).concat(yRDelimit2).concat(yCdtRevOurRef);

        // Set the CreditLeg for the AcctEntries2
        String yDdtRevPlCategory = "";
        String yDdtRevAccount = "";
        String yDdtRevAmount = "";
        String yDdtRevCurrency = "";
        String yDdtRevTranSign = "D";
        String yDdtRevTransReference = "";
        String yDdtRevCustomer = "";
        String yDdtRevAcctOfficer = "";
        String yDdtRevProdCateg = "";
        String yDdtRevOurRef = "";
        yDdtRevAccount = getLcyPositioningAcct(yCdtRevAccount);
        yDdtRevAmount = yCdtRevAmount;
        yDdtRevCurrency = ySession.getLocalCurrency();
        yDdtRevTransReference = accountingEntry.getTransReference().getValue();
        yDdtRevCustomer = accountingEntry.getCustomerId().getValue();
        yDdtRevAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        yDdtRevProdCateg = accountingEntry.getProductCategory().getValue();
        yDdtRevOurRef = accountingEntry.getOurReference().getValue();
        // Forming CreditLeg Message
        String yDbtRevLeg2Entries = yBulkNm.concat(yRDelimit2).concat(yDTransCode).concat(yRDelimit2)
                .concat(yDdtRevPlCategory).concat(yRDelimit2).concat(yDdtRevAccount).concat(yRDelimit2).concat(yDdtRevAmount)
                .concat(yRDelimit2).concat(yDdtRevCurrency).concat(yRDelimit2).concat(yDdtRevTranSign).concat(yRDelimit2)
                .concat(yDdtRevTransReference).concat(yRDelimit2).concat(yDdtRevCustomer).concat(yRDelimit2)
                .concat(yDdtRevAcctOfficer).concat(yRDelimit2).concat(yDdtRevProdCateg).concat(yRDelimit2).concat(yDdtRevOurRef);

        return yDbtRevLeg2Entries.concat("#").concat(yCdtRevLeg2Entries).concat("**").concat("Leg2");
    }

    //
    private void processInputVatCalculation(StmtEntryRecord accountingEntry, EbCsdVatWhtRateParamRecord yVatWhtParamRec,
            int bestFitIndex, EbCsdTaxDetailsUpdRecord yTaxDetsUpdRec, String acctNumber, String taxRateVal, AccountRecord yAcctRec) {
        String inpRevEntFlag = "N";
        String inputVatCalcAmt = "";
        String exchangeRate = "";
        String whtCalcAmt = "";
        String plType=yVatWhtParamRec.getClientCategory(bestFitIndex).getPlType().getValue();
        String systemId = accountingEntry.getSystemId().getValue();
        vatCalcType = yVatWhtParamRec.getClientCategory(bestFitIndex).getInputVatCalculationType().getValue();
        whtCalcType = yVatWhtParamRec.getClientCategory(bestFitIndex).getWhtCalculationType().getValue();

        if (!accountingEntry.getAmountFcy().getValue().isBlank()) {
            flagFcy = true;
            exchangeRate = calculateExhangeRate(accountingEntry);
        }
        if (!vatCalcType.equals("") && whtCalcType.equals("")) {
            logger.debug("*** Input VAT alone condition satisfied ***");
            inputVatCalcAmt = calculateInputVatAmt(accountingEntry, yVatWhtParamRec, bestFitIndex);
            logger.debug("String inputVatCalcAmt = "+inputVatCalcAmt);
        }
        else if (!vatCalcType.equals("") && !whtCalcType.equals("")) {
            logger.debug("*** Input VAT+WHT condition satisfied ***");
            String tempinputVatCalcAmt = calculateInputVatAmt(accountingEntry, yVatWhtParamRec, bestFitIndex);
            inputVatCalcAmt = tempinputVatCalcAmt.split("\\*")[0];
            whtCalcAmt = tempinputVatCalcAmt.split("\\*")[1];
            whtInd = true;
            logger.debug("String inputVatCalcAmt = "+inputVatCalcAmt);
            logger.debug("String whtCalcAmt = "+whtCalcAmt);
        }
        
        if (plType.equalsIgnoreCase(INTEREST)) { //--->
            updAcctEntriesDets(accountingEntry, inputVatCalcAmt, yVatWhtParamRec, exchangeRate, acctNumber, "", plType);
            if (whtInd) {
                updNostroWhtEntriesDets(accountingEntry, whtCalcAmt, yVatWhtParamRec, plType);
                updateInpInterestDetails(accountingEntry, whtCalcAmt, inputVatCalcAmt, yVatWhtParamRec, bestFitIndex,yTaxDetsUpdRec);
            }
            
        } else if (plType.equalsIgnoreCase(FEE)) {
            logger.debug(" ***** Input VAT Fee Condition satisfied ***** ");
            exchangeRate = calculateExhangeRate(accountingEntry);
            logger.debug("String exchangeRate = "+exchangeRate);
            if (!accountingEntry.getPlCategory().getValue().isBlank() && 
                    accountingEntry.getAmountLcy().getValue().startsWith("-")) {
                logger.debug(" ***** Input VAT Fee Pl Debit Condition satisfied ***** ");
                String feeTransDets = updateChgDets(yAcctRec, systemId, accountingEntry, inputVatCalcAmt, exchangeRate,
                        taxRateVal, "NO");
                logger.debug("String feeTransDets = "+feeTransDets);
                updAcctEntriesDets(accountingEntry, inputVatCalcAmt, yVatWhtParamRec, exchangeRate, acctNumber,
                        feeTransDets, plType);
                if (whtInd) {
                    updNostroWhtEntriesDets(accountingEntry, whtCalcAmt, yVatWhtParamRec, plType);
                }
            } else if (!accountingEntry.getPlCategory().getValue().isBlank() && 
                    !accountingEntry.getAmountLcy().getValue().startsWith("-")) {
                inpRevEntFlag = "R";
                logger.debug(" ***** Input VAT Fee Pl Credit Condition satisfied ***** ");
                String feeTransDets = updateChgDets(yAcctRec, systemId, accountingEntry, inputVatCalcAmt, exchangeRate,
                        taxRateVal, "YES");
                logger.debug("String feeTransDets = "+feeTransDets);
                updReversalFeeAcctEnt(accountingEntry, inputVatCalcAmt, yVatWhtParamRec, exchangeRate, acctNumber, feeTransDets, plType);
                if (whtInd) {
                    updRevwhtFeeAcctEnt(accountingEntry, whtCalcAmt, yVatWhtParamRec, plType);
                }
            }
            writeAcctIdDets(acctNumber, inpRevEntFlag);
        }
    }

    
    //
    private void updRevwhtFeeAcctEnt(StmtEntryRecord accountingEntry, String whtCalcAmt,
            EbCsdVatWhtRateParamRecord yVatWhtParamRec, String plType) {
        String acctEntDelimit = ",";
        String yProMode="";
        String yCdtPlCateg = "";
        String yCdtAccount = "";
        String yCdtAmount = "";
        String yCdtCcy = "";
        String yCdtTranSign = "C";
        String yCdtTransRef = "";
        String yCdtCustomer = "";
        String yCdtAcctOff = "";
        String yCdtProdCateg = "";
        String yCdtOurRef = "";
        yCdtOurRef = accountingEntry.getOurReference().getValue();
        yCdtPlCateg = accountingEntry.getPlCategory().getValue();
        yCdtCcy = ySession.getLocalCurrency();
        if (flagFcy) {
            yProMode = "CSM";
            yCdtAmount = getExchangeCurrencyAmt(whtCalcAmt, accountingEntry.getExchangeRate().getValue());
        } else {
            yProMode = yBulkNm ;
            yCdtAmount = whtCalcAmt;
        }

        yCdtTransRef = accountingEntry.getTransReference().getValue();
        yCdtCustomer = accountingEntry.getCustomerId().getValue();
        yCdtAcctOff = accountingEntry.getAccountOfficer().getValue();
        yCdtProdCateg = accountingEntry.getProductCategory().getValue();
        // Forming DebitLeg Message
        String cdtLegEntries = yProMode.concat(acctEntDelimit).concat(yCTransCode).concat(acctEntDelimit)
                .concat(yCdtPlCateg).concat(acctEntDelimit).concat(yCdtAccount).concat(acctEntDelimit).concat(yCdtAmount)
                .concat(acctEntDelimit).concat(yCdtCcy).concat(acctEntDelimit).concat(yCdtTranSign).concat(acctEntDelimit)
                .concat(yCdtTransRef).concat(acctEntDelimit).concat(yCdtCustomer).concat(acctEntDelimit)
                .concat(yCdtAcctOff).concat(acctEntDelimit).concat(yCdtProdCateg).concat(acctEntDelimit).concat(yCdtOurRef);

        // Set the CreditLeg for the AcctEntries1
        String yDbtplcateg = "";
        String yDbtaccount = "";
        String yDbtamount = "";
        String yDbtccy = "";
        String yDbttranSign = "D";
        String yDbttransRef = "";
        String yDbtcustomer = "";
        String yDbtacctOff = "";
        String yDbtprodCateg = "";
        String yDbtourRef = "";
        yDbtaccount = getvatPayAcct(yVatWhtParamRec.getWhtPayableAccount().getValue(), accountingEntry);

        yDbtamount = whtCalcAmt;
        yDbtccy = accountingEntry.getCurrency().getValue();
        yDbttransRef = accountingEntry.getTransReference().getValue();
        yDbtcustomer = accountingEntry.getCustomerId().getValue();
        yDbtacctOff = accountingEntry.getAccountOfficer().getValue();
        yDbtprodCateg = accountingEntry.getProductCategory().getValue();
        yDbtourRef = accountingEntry.getOurReference().getValue();
        // Forming CreditLeg Message
        String dbtLegEntries = yProMode.concat(acctEntDelimit).concat(yDTransCode).concat(acctEntDelimit)
                .concat(yDbtplcateg).concat(acctEntDelimit).concat(yDbtaccount).concat(acctEntDelimit).concat(yDbtamount)
                .concat(acctEntDelimit).concat(yDbtccy).concat(acctEntDelimit).concat(yDbttranSign).concat(acctEntDelimit)
                .concat(yDbttransRef).concat(acctEntDelimit).concat(yDbtcustomer).concat(acctEntDelimit)
                .concat(yDbtacctOff).concat(acctEntDelimit).concat(yDbtprodCateg).concat(acctEntDelimit).concat(yDbtourRef);

        EbCsdAcctEntDetailsRecord acctEntDetsRec = new EbCsdAcctEntDetailsRecord(this);
        acctEntDetsRec.setAccountingEntry(dbtLegEntries.concat("#").concat(cdtLegEntries).concat("**Leg1"));
        // Forming Unique Reference Number followed with account number
        String yUniqueId1 = "";
        yUniqueId1 = UUID.randomUUID().toString().concat("$WHT");
        // Write the details into EB.CSD.ACCT.ENT.DETAILS
        writeAcctEntDets(yUniqueId1, acctEntDetsRec);
        if (flagFcy) {
            String exchangeRateWht = calculateExhangeRate(accountingEntry);
            EbCsdAcctEntDetailsRecord acctEntDetsRecObj = new EbCsdAcctEntDetailsRecord(this);
            String outputVatCalcAmt = "";
            String acctEntries2 = getRevLeg2AcctEntries(exchangeRateWht, yDbtamount, yDbtaccount, accountingEntry,
                    outputVatCalcAmt, plType);
            acctEntDetsRecObj.setAccountingEntry(acctEntries2);
            // Forming Unique Reference Number followed with account number
            writeAcctEntDets(UUID.randomUUID().toString().concat("$WHT"), acctEntDetsRecObj);
        }  
    }

    //
    private void updateInpInterestDetails(StmtEntryRecord accountingEntry, String whtCalcAmt, String inputVatCalcAmt,
            EbCsdVatWhtRateParamRecord yVatWhtParamRec, int bestFitIndex, EbCsdTaxDetailsUpdRecord yTaxDetsUpdRec) {
        String acctNumber = getAcctNum(accountingEntry);
        String exchangeRateWht = calculateExhangeRate(accountingEntry);
        whtInd = false;
        String exchangeRateVat = calculateExhangeRate(accountingEntry);
        String vatTaxTypeCond = yVatWhtParamRec.getClientCategory(bestFitIndex).getVatRate().getValue();
        String vatTaxcde = fetchTaxCode(vatTaxTypeCond);
        String vatTaxRateVal = fetchTaxRate(vatTaxcde);
        String whtTaxTypeCond = yVatWhtParamRec.getClientCategory(bestFitIndex).getWhtRate().getValue();
        String whtTaxcde = fetchTaxCode(whtTaxTypeCond);
        String whtTaxRateVal = fetchTaxRate(whtTaxcde);

        InterestDetailClass nosInterestDets = new InterestDetailClass();
        nosInterestDets.setInputVatRateForInterest(vatTaxRateVal);
        nosInterestDets.setVatExchangeRateForInterest(exchangeRateVat);
        nosInterestDets.setWhtRateForInterest(whtTaxRateVal);
        nosInterestDets.setWhtExchangeRateForInterest(exchangeRateWht);
        nosInterestDets.setInterestTransactionType(yExpense);
        nosInterestDets.setInterestCurrency(accountingEntry.getCurrency().getValue());
        int totalSize = yTaxDetsUpdRec.getInterestDetail().size();
        PeriodStartClass periodRcd = new PeriodStartClass();
        periodRcd.setPeriodEnd(accountingEntry.getBookingDate().getValue());
        if (flagFcy) {
            periodRcd.setTotalIntAmount(new BigDecimal(accountingEntry.getAmountFcy().getValue()).abs().toString());
            periodRcd.setTotInputVatFcyAmtForInt(inputVatCalcAmt);
            periodRcd.setTotInputValLcyAmtForInt(getExchangeCurrencyAmt(inputVatCalcAmt, exchangeRateVat));
            periodRcd.setTotWhtFcyAmtForInt(whtCalcAmt);
            periodRcd.setTotWhtLcyAmtForInt(getExchangeCurrencyAmt(whtCalcAmt, exchangeRateWht));
        } else {
            periodRcd.setTotalIntAmount(new BigDecimal(accountingEntry.getAmountLcy().getValue()).abs().toString());
            periodRcd.setTotInputValLcyAmtForInt(inputVatCalcAmt);
            periodRcd.setTotWhtLcyAmtForInt(whtCalcAmt);
        }
        nosInterestDets.setPeriodStart(periodRcd, 0);
        yTaxDetsUpdRec.setInterestDetail(nosInterestDets, totalSize);
        writeDetstoLiveTable(yTaxDetsUpdRec, acctNumber);
    }

    //
    private void updNostroWhtEntriesDets(StmtEntryRecord accountingEntry, String whtCalcAmt,
            EbCsdVatWhtRateParamRecord yVatWhtParamRec, String plType) {

        String yDelimit1 = ",";
        String processMode="";
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
        dbtOurRef = accountingEntry.getOurReference().getValue();
        dbtPlCategory = accountingEntry.getPlCategory().getValue();
        dbtCurrency = ySession.getLocalCurrency();
        if (flagFcy) {
             processMode = "CSM";
            dbtAmount = getExchangeCurrencyAmt(whtCalcAmt, accountingEntry.getExchangeRate().getValue());
        } else {
             processMode = yBulkNm ;
            dbtAmount = whtCalcAmt;
        }

        dbtTransReference = accountingEntry.getTransReference().getValue();
        dbtCustomer = accountingEntry.getCustomerId().getValue();
        dbtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        dbtProdCateg = accountingEntry.getProductCategory().getValue();
        // Forming DebitLeg Message
        String debitLegEntries = processMode.concat(yDelimit1).concat(yDTransCode).concat(yDelimit1)
                .concat(dbtPlCategory).concat(yDelimit1).concat(dbtAccount).concat(yDelimit1).concat(dbtAmount)
                .concat(yDelimit1).concat(dbtCurrency).concat(yDelimit1).concat(dbtTranSign).concat(yDelimit1)
                .concat(dbtTransReference).concat(yDelimit1).concat(dbtCustomer).concat(yDelimit1)
                .concat(dbtAcctOfficer).concat(yDelimit1).concat(dbtProdCateg).concat(yDelimit1).concat(dbtOurRef);

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
        cdtAccount = getvatPayAcct(yVatWhtParamRec.getWhtPayableAccount().getValue(), accountingEntry);

        cdtAmount = whtCalcAmt;
        cdtCurrency = accountingEntry.getCurrency().getValue();
        cdtTransReference = accountingEntry.getTransReference().getValue();
        cdtCustomer = accountingEntry.getCustomerId().getValue();
        cdtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        cdtProdCateg = accountingEntry.getProductCategory().getValue();
        cdtOurRef = accountingEntry.getOurReference().getValue();
        // Forming CreditLeg Message
        String creditLegEntries = processMode.concat(yDelimit1).concat(yCTransCode).concat(yDelimit1)
                .concat(cdtPlCategory).concat(yDelimit1).concat(cdtAccount).concat(yDelimit1).concat(cdtAmount)
                .concat(yDelimit1).concat(cdtCurrency).concat(yDelimit1).concat(cdtTranSign).concat(yDelimit1)
                .concat(cdtTransReference).concat(yDelimit1).concat(cdtCustomer).concat(yDelimit1)
                .concat(cdtAcctOfficer).concat(yDelimit1).concat(cdtProdCateg).concat(yDelimit1).concat(cdtOurRef);

        EbCsdAcctEntDetailsRecord acctEntDetsRec = new EbCsdAcctEntDetailsRecord(this);
        acctEntDetsRec.setAccountingEntry(debitLegEntries.concat("#").concat(creditLegEntries).concat("**Leg1"));
        // Forming Unique Reference Number followed with account number
        String yUniqueId1 = "";
        yUniqueId1 = UUID.randomUUID().toString().concat("$WHT");
        // Write the details into EB.CSD.ACCT.ENT.DETAILS
        writeAcctEntDets(yUniqueId1, acctEntDetsRec);
        if (flagFcy) {
            String exchangeRateWht = calculateExhangeRate(accountingEntry);
            EbCsdAcctEntDetailsRecord acctEntDetsRecObj = new EbCsdAcctEntDetailsRecord(this);
            String outputVatCalcAmt = "";
            String acctEntries2 = getAcctEntries2(exchangeRateWht, cdtAmount, cdtAccount, accountingEntry,
                    outputVatCalcAmt, plType);
            acctEntDetsRecObj.setAccountingEntry(acctEntries2);
            // Forming Unique Reference Number followed with account number
            writeAcctEntDets(UUID.randomUUID().toString().concat("$WHT"), acctEntDetsRecObj);

        }
    }

    //
    private CustomerRecord getCustomerDets(StmtEntryRecord accountingEntry) {
        String custId = "";
        if (accountingEntry.getSystemId().getValue().equals("PP")) {
            custId = accountingEntry.getCustomerId().getValue();
            if (custId.isBlank()) {
                custId = getLocalRefFld(accountingEntry.getTransReference().getValue());
            }
            bstFitCustId = custId;
        }
        if (accountingEntry.getSystemId().getValue().equals(ftIden)) {
            custId = accountingEntry.getCustomerId().getValue();
            if (custId.isBlank()) {
                custId = getFtCustId(accountingEntry.getTransReference().getValue());
            }
            bstFitCustId = custId;
        }
        if (accountingEntry.getSystemId().getValue().equals(accIden) || 
                accountingEntry.getSystemId().getValue().equals(arrIden)) {
            custId = accountingEntry.getCustomerId().getValue();
            bstFitCustId = custId;
        }
        logger.debug("String bstFitCustId = "+bstFitCustId);
        return getCustRecord(bstFitCustId);
    }

    //
    private CustomerRecord getCustRecord(String custId) {
        CustomerRecord yCustRec = new CustomerRecord(this);
        try {
            yCustRec = new CustomerRecord(
                    da.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "CUSTOMER", "", custId));
        } catch (Exception e) {
            //
        }
        return yCustRec;
    }

    //
    private String getFtCustId(String transRef) {
        String ftCustId = "";
        try {
            FundsTransferRecord yFtRecord = new FundsTransferRecord(da.getRecord(
                    ySession.getCompanyRecord().getFinancialMne().getValue(), "FUNDS.TRANSFER", "", transRef));
            ftCustId = yFtRecord.getLocalRefField(CSD_CUSTOMER_ID).getValue();
        } catch (Exception e) {
            //
        }
        return ftCustId;
    }

    //
    private String getLocalRefFld(String transRef) {
        String poCustId = "";
        try {
            PorTransactionRecord yPorTransRec = new PorTransactionRecord(da.getRecord(POR_TRANSACTION, transRef));
            if (yPorTransRec.getSendersreferenceincoming().getValue().startsWith("PI")) {
                PaymentOrderRecord yPORec = new PaymentOrderRecord(da.getRecord(
                        ySession.getCompanyRecord().getFinancialMne().getValue(), "PAYMENT.ORDER", "", yPorTransRec.getSendersreferenceincoming().getValue()));
                poCustId = yPORec.getLocalRefField(CSD_CUSTOMER_ID).getValue();
            } else {
                PpOrderEntryRecord orderEntRec = getOrderEntryRec(transRef);
                poCustId = orderEntRec.getLocalRefField(CSD_CUSTOMER_ID).getValue();
            }
        } catch (Exception e) {
            //
        }
        return poCustId;
    }

    //
    private String calculateInputVatAmt(StmtEntryRecord accountingEntry, EbCsdVatWhtRateParamRecord yVatWhtParamRec,
            int bestFitIndex) {
        String returnResult="";
        BigDecimal vatAmt;
        BigDecimal whtAmt;
        if (!accountingEntry.getAmountLcy().getValue().isBlank()) {
            String vatTaxTypeCond = yVatWhtParamRec.getClientCategory(bestFitIndex).getVatRate().getValue();
            String vatTaxcde = fetchTaxCode(vatTaxTypeCond);
            String vatTaxRateVal = fetchTaxRate(vatTaxcde);
            String whtTaxTypeCond = yVatWhtParamRec.getClientCategory(bestFitIndex).getWhtRate().getValue();
            String whtTaxcde = fetchTaxCode(whtTaxTypeCond);
            String whtTaxRateVal = fetchTaxRate(whtTaxcde);
            vatEmpty = checkVatEmpty();
            whtEmpty = checkWhtEmpty();
            BigDecimal grossAmt;
            if (flagFcy) {
                grossAmt = new BigDecimal(accountingEntry.getAmountFcy().getValue());
            } else {
                grossAmt = new BigDecimal(accountingEntry.getAmountLcy().getValue());
            }
            if (!vatEmpty && whtEmpty) {
                logger.debug("*** Input VAT alone Calculation cond satisfied ***");
                vatAmt = calculateOnlyInpVat(grossAmt, vatTaxRateVal);
                returnResult = vatAmt.abs().toString();
                logger.debug("String returnResult = "+returnResult);
            }

            if (!vatEmpty && !whtEmpty) {
                logger.debug("*** Input VAT+WHT Calculation cond satisfied ***");
                VatWhtResult result = null;
                BigDecimal vat = new BigDecimal(vatTaxRateVal);
                BigDecimal wht = new BigDecimal(whtTaxRateVal);
                result = getVatWhtResult(grossAmt,vat,wht,result);
                if (result == null)
                    returnResult = "";
                else {
                    vatAmt = result.vatAmount;
                    whtAmt = result.whtAmount;
                    returnResult = vatAmt.toString().concat("*").concat(whtAmt.toString());
                }
                logger.debug("String returnResult = "+returnResult);
            }

        }
        return returnResult;
    }

    //
    private boolean checkWhtEmpty() {
        return whtCalcType == null || whtCalcType.isBlank();
    }

    //
    private boolean checkVatEmpty() {
        return vatCalcType == null || vatCalcType.isBlank();
    }

    //
    private VatWhtResult getVatWhtResult(BigDecimal grossAmt, BigDecimal vat, BigDecimal wht, VatWhtResult result) {

        if (vatCalcType.equals(INCLUSIVE) && whtCalcType.equals(EXCLUSIVE))
            result = CsdWithStandTaxCalc.calculateVatWhtIncExc(grossAmt, vat, wht);
        if (vatCalcType.equals(INCLUSIVE) && whtCalcType.equals(INCLUSIVE))
            result = CsdWithStandTaxCalc.calculateVatWhtInclusive(grossAmt, vat, wht);
        if (vatCalcType.equals(EXCLUSIVE) && whtCalcType.equals(INCLUSIVE))
            result = CsdWithStandTaxCalc.calculateVatWhtExcInc(grossAmt, vat, wht);
        if (vatCalcType.equals(EXCLUSIVE) && whtCalcType.equals(EXCLUSIVE))
            result = CsdWithStandTaxCalc.calculateVatWhtExclusive(grossAmt, vat, wht);
       
        return result;
    }
    
    //
    private BigDecimal calculateOnlyInpVat(BigDecimal grossAmt, String vatTaxRateVal) {
        BigDecimal formulaVal = BigDecimal.ZERO;
        BigDecimal hundred = new BigDecimal("100");
        BigDecimal one = new BigDecimal("1");
        BigDecimal vatRateper = new BigDecimal(vatTaxRateVal);
        try {
            if (!vatEmpty && whtEmpty) {
                // Only VAT available
                BigDecimal denominator = vatRateper.divide(hundred, 10, RoundingMode.HALF_UP);
                switch (vatCalcType) {
                case INCLUSIVE:
                    // Gross Amount / ((1+Input VAT rate%) * Input VAT rate%).
                    BigDecimal denominator1 = denominator.add(one);
                    formulaVal = grossAmt.divide(denominator1, 2, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal formulaVal1 = formulaVal.multiply(denominator).setScale(2, RoundingMode.HALF_UP);
                    formulaVal = formulaVal1;
                    break;

                case EXCLUSIVE:
                    // Trans amount * Input Vat %
                    formulaVal = grossAmt.multiply(denominator).setScale(2, RoundingMode.HALF_UP);
                    break;
                default:

                }
            }
        } catch (Exception e) {
            //
        }
        return formulaVal.abs();

    }

    //
    private String getInterestProp(StmtEntryRecord accountingEntry, AccountRecord yAcctRec) {
        String interstProperty = "";
        try {
            interstProperty = accountingEntry.getTransReference().getValue().split("-")[1];
        } catch (Exception e) {
            Contract yContract = new Contract(this);
            yContract.setContractId(yAcctRec.getArrangementId().getValue());
            interstProperty = yContract.getPropertyIdsForPropertyClass("INTEREST").get(0);
        }
        return interstProperty;
    }

        //
    private EbCsdTaxDetsParamRecord getTaxDetsParamRecord() {
        EbCsdTaxDetsParamRecord ytaxDetsParamRec = new EbCsdTaxDetsParamRecord(this);
        try {
            ytaxDetsParamRec = new EbCsdTaxDetsParamRecord(da.getRecord(
                    ySession.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.TAX.DETS.PARAM", "", ySystemId));
        } catch (Exception e) {
            //
        }
        return ytaxDetsParamRec;
    }

    //
    private String getLcyPositioningAcct(String yDbtAccount) {
        String internalLcyAcct = "";
        try {
            logger.debug("*** getLcyPositioningAcct method called ***");
            String localCcy = ySession.getLocalCurrency(); // get the local currency
            logger.debug("String localCcy = "+ localCcy);
            logger.debug("*** getLcyPositioningAcct method localCcy" +localCcy);
            String subDivCode = ySession.getCompanyRecord().getSubDivisionCode().getValue();
            logger.debug("String subDivCode = "+ subDivCode);
            logger.debug("*** getLcyPositioningAcct method called subDivCode" +subDivCode);
            String categoryId = yDbtAccount.substring(3, 8); // Get the Category
            logger.debug("String categoryId = "+ categoryId);
            logger.debug("*** getLcyPositioningAcct method categoryId" +categoryId);
            String lcyIntAcctId = localCcy.concat(categoryId).concat(subDivCode);
            logger.debug("*** getLcyPositioningAcct method = lcyIntAcctId"+ lcyIntAcctId);
            List<String> categIntAcctList = da.getConcatValues("CATEG.INT.ACCT", categoryId);
            logger.debug("String categIntAcctList = "+ categIntAcctList);
            internalLcyAcct = categIntAcctList.stream().filter(code -> code.startsWith(lcyIntAcctId)).findFirst()
                    .orElse(""); // Get the internal account for the respective currency
            logger.debug("*** getLcyPositioningAcct method = internalLcyAcct"+ internalLcyAcct);
            
            if (internalLcyAcct.isEmpty()) {
                logger.debug("*** getLcyPositioningAcct method internalLcyAcct is empty ");
            String lcyIntCatAcctId = localCcy.concat(categoryId);
            logger.debug("*** getLcyPositioningAcct method internalLcyAcct is empty case lcyIntCatAcctId" +lcyIntCatAcctId);
            logger.debug("String lcyIntAcctId = "+ lcyIntCatAcctId);
            
            internalLcyAcct = categIntAcctList.stream().filter(code -> code.startsWith(lcyIntCatAcctId)).findFirst()
                    .orElse(""); // Get the internal account for the respective currency
            logger.debug("*** getLcyPositioningAcct method internalLcyAcct is empty case internalLcyAcct" +internalLcyAcct);
            }
            logger.debug("String finally internalLcyAcct = "+ internalLcyAcct);
        } catch (Exception e) {
            //
            logger.debug("getLcyPositioningAcct method exception e = "+e);
        }
        logger.debug("getLcyPositioningAcct method finally  internalLcyAcct = "+internalLcyAcct);
        return internalLcyAcct;
    }

    //
    private String getExchangeCurrencyAmt(String outputVatAmt, String exchangeRate) {
        String yExchngRateCcyAmt = "";
        try {
            BigDecimal yBdOutputVat = new BigDecimal(outputVatAmt);
            BigDecimal yBdExchngRate = new BigDecimal(exchangeRate);
            yExchngRateCcyAmt = yBdOutputVat.multiply(yBdExchngRate).setScale(2, RoundingMode.HALF_UP).toString();
        } catch (Exception e) {
            //
        }
        return yExchngRateCcyAmt;
    }

    //
    private String calculateOutputVatAmt(StmtEntryRecord accountingEntry, String taxRateVal) {
        String yOutputVatCalcAmt = "";
        if (!accountingEntry.getAmountLcy().getValue().isBlank()
                && accountingEntry.getAmountFcy().getValue().isBlank()) {
            yOutputVatCalcAmt = calculateTaxAmt(accountingEntry.getAmountLcy().getValue(), taxRateVal);
        } else if (!accountingEntry.getAmountFcy().getValue().isBlank()) {
            yOutputVatCalcAmt = calculateTaxAmt(accountingEntry.getAmountFcy().getValue(), taxRateVal);
        }
        return yOutputVatCalcAmt;
    }

    //
    private String calculateExhangeRate(StmtEntryRecord accountingEntry) {
        String yExchangeRate = "";
        if (!accountingEntry.getAmountFcy().getValue().isBlank()) {
            CurrencyRecord yCurrency = new CurrencyRecord(
                    da.getRecord("CURRENCY", accountingEntry.getCurrency().getValue()));
            yExchangeRate = getExchangeRate(yCurrency, getCurrMarketVal(da));
        }
        return yExchangeRate;
    }

    //
    private EbCsdTaxDetailsUpdRecord updateCommonData(CustomerRecord yCustRec, StmtEntryRecord accountingEntry,
            AccountRecord yAcctRec, EbCsdTaxDetailsUpdRecord yTaxDetsUpdRec) {
        try {
            // Update the Transaction Details into the Live Table
            if (!yLiveRecFlag) {
                ClientBrdIdClass yCustBrdObj = new ClientBrdIdClass();
                yCustBrdObj.setClientBrdId(bstFitCustId);
                yCustBrdObj.setClientCategory(yCustRec.getSector().getValue());
                yCustBrdObj.setClientLocation(yCustRec.getRegCountry().getValue());
                yTaxDetsUpdRec.setClientBrdId(yCustBrdObj, 0);
                yTaxDetsUpdRec.setAccountNumber(accountingEntry.getOurReference().getValue());
                yTaxDetsUpdRec.setArrangementNumber(yAcctRec.getArrangementId().getValue());
                yTaxDetsUpdRec.setAccountCategory(yAcctRec.getCategory().getValue());
                yTaxDetsUpdRec.setAccountCurrency(yAcctRec.getCurrency().getValue());
            }
            else {
                updateClientBrdId(yTaxDetsUpdRec,  yCustRec);
            }
        } catch (Exception e) {
            //
        }
        return yTaxDetsUpdRec;
    }

    //
    private EbCsdTaxDetailsUpdRecord updateClientBrdId(EbCsdTaxDetailsUpdRecord yTaxDetsUpdRec,  CustomerRecord yCustRec) {
        boolean custIdFlag = false;
        for (ClientBrdIdClass tClientBrdId : yTaxDetsUpdRec.getClientBrdId()) {
            if (tClientBrdId.getClientBrdId().getValue().equals(bstFitCustId)) {
                custIdFlag = true;
                break;
            }
        }
        if (!custIdFlag) {
            ClientBrdIdClass cltBrdIdObj = new ClientBrdIdClass();
            cltBrdIdObj.setClientBrdId(bstFitCustId);
            cltBrdIdObj.setClientCategory(yCustRec.getSector().getValue());
            cltBrdIdObj.setClientLocation(yCustRec.getRegCountry().getValue());
            yTaxDetsUpdRec.setClientBrdId(cltBrdIdObj, yTaxDetsUpdRec.getClientBrdId().size());
        }
        return yTaxDetsUpdRec;
    }
    
    //
    private String getCurrMarketVal(DataAccess da) {
        String currMarketId = "";
        try {
            EbCsdTaxDetsParamRecord ytaxDetsParamRec = new EbCsdTaxDetsParamRecord(da.getRecord(
                    ySession.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.TAX.DETS.PARAM", "", ySystemId));
            String paramId = "";
            if (whtInd) {
                paramId = ytaxDetsParamRec.getWhtEntryParam().getValue();
            } else {
                paramId = ytaxDetsParamRec.getVatEntryParam().getValue();
            }
            AcEntryParamRecord yEntryParamRec = new AcEntryParamRecord(da.getRecord("AC.ENTRY.PARAM", paramId));
            currMarketId = yEntryParamRec.getCurrencyMarket().getValue();
        } catch (Exception e) {
            //
        }
        return currMarketId;
    }

    //
    private String fetchTaxRate(String taxcde) {
        String taxRate = "";
        try {
            List<String> taxCodeList = da.selectRecords("", "TAX", "", "WITH @ID LIKE " + taxcde + "....");
            TaxRecord ytaxRec = new TaxRecord(da.getRecord("TAX", getLatestTaxId(taxCodeList)));
            taxRate = ytaxRec.getRate().getValue();
        } catch (Exception e) {
            //
        }
        return taxRate;
    }

    //
    private String getLatestTaxId(List<String> taxCodeList) {
        String latestTaxId = "";
        if (taxCodeList.size() == 1) {
            latestTaxId = taxCodeList.get(0);
        }
        // if the id list is GT 1, split the date component from the id
        if (taxCodeList.size() > 1) {
            List<Integer> datesList = new ArrayList<>();
            String[] taxCodeDets = {};
            for (String tempCode : taxCodeList) {
                taxCodeDets = tempCode.split("\\.");
                datesList.add(Integer.parseInt(taxCodeDets[1]));
            }
            if (!datesList.isEmpty()) {
                latestTaxId = taxCodeDets[0] + "." + Collections.max(datesList).toString();
            }
        }
        return latestTaxId;
    }

    //
    private String fetchTaxCode(String taxTypeCond) {
        String taxcode = "";
        try {
            TaxTypeConditionRecord yTaxTypeCondRec = new TaxTypeConditionRecord(da.getRecord(
                    ySession.getCompanyRecord().getFinancialMne().getValue(), "TAX.TYPE.CONDITION", "", taxTypeCond));
            int custTaxGrpLen = yTaxTypeCondRec.getCustTaxGrp().size();
            if (yTaxTypeCondRec.getCustTaxGrp().size() > 0) {
                int contractGrpLen = yTaxTypeCondRec.getCustTaxGrp(custTaxGrpLen - 1).getContractGrp().size();
                if (contractGrpLen > 0) {
                    taxcode = yTaxTypeCondRec.getCustTaxGrp(custTaxGrpLen - 1).getContractGrp(contractGrpLen - 1)
                            .getTaxCode().getValue();
                }
            }
        } catch (Exception e) {
            //
        }
        return taxcode;
    }

    //
    private void updAcctEntriesDets(StmtEntryRecord accountingEntry, String outputVatCalcAmt,
            EbCsdVatWhtRateParamRecord yVatWhtParamRec, String exchangeRate, String acctNumber, String feeTransDets,
            String plType) {
        // Set the Process mode based on lcy and fcy
        String yDelimit1 = ",";
        String processMode = "";
        if (accountingEntry.getAmountFcy().getValue().isBlank() || (inputVatind)) {
            processMode = yBulkNm ;
        } else {
            processMode = "CSM";
        }
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
        dbtOurRef = accountingEntry.getOurReference().getValue();

        if (taxType.equalsIgnoreCase(INPUT)) {
            dbtAccount = getvatPayAcct(yVatWhtParamRec.getInputVatTempAccount().getValue(), accountingEntry);
        } else if (taxType.equalsIgnoreCase(OUTPUT) || whtInd) {
            dbtPlCategory = accountingEntry.getPlCategory().getValue();
            whtInd = false;
        }

        dbtAmount = getFcyWotConv(outputVatCalcAmt, plType, accountingEntry, exchangeRate);
        dbtCurrency = accountingEntry.getCurrency().getValue();
        dbtTransReference = accountingEntry.getTransReference().getValue();
        dbtCustomer = accountingEntry.getCustomerId().getValue();
        dbtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        dbtProdCateg = accountingEntry.getProductCategory().getValue();
        // Forming DebitLeg Message
        String debitLegEntries = processMode.concat(yDelimit1).concat(yDTransCode).concat(yDelimit1)
                .concat(dbtPlCategory).concat(yDelimit1).concat(dbtAccount).concat(yDelimit1).concat(dbtAmount)
                .concat(yDelimit1).concat(dbtCurrency).concat(yDelimit1).concat(dbtTranSign).concat(yDelimit1)
                .concat(dbtTransReference).concat(yDelimit1).concat(dbtCustomer).concat(yDelimit1)
                .concat(dbtAcctOfficer).concat(yDelimit1).concat(dbtProdCateg).concat(yDelimit1).concat(dbtOurRef);

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
        if (taxType.equals(INPUT) && !vatCalcType.equals("") && vatCalcType.equalsIgnoreCase(INCLUSIVE) && !flagFcy) {
            cdtPlCategory = accountingEntry.getPlCategory().getValue();
        } else if (taxType.equals(OUTPUT)
                || (taxType.equals(INPUT) && !vatCalcType.equals("") && vatCalcType.equalsIgnoreCase(EXCLUSIVE))
                || checkIncFcy()) {
            cdtAccount = getvatPayAcct(yVatWhtParamRec.getVatPayableAccount().getValue(), accountingEntry);
        }

        cdtAmount = dbtAmount;
        cdtCurrency = accountingEntry.getCurrency().getValue();
        cdtTransReference = accountingEntry.getTransReference().getValue();
        cdtCustomer = accountingEntry.getCustomerId().getValue();
        cdtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        cdtProdCateg = accountingEntry.getProductCategory().getValue();
        cdtOurRef = accountingEntry.getOurReference().getValue();
        // Forming CreditLeg Message
        String creditLegEntries = processMode.concat(yDelimit1).concat(yCTransCode).concat(yDelimit1)
                .concat(cdtPlCategory).concat(yDelimit1).concat(cdtAccount).concat(yDelimit1).concat(cdtAmount)
                .concat(yDelimit1).concat(cdtCurrency).concat(yDelimit1).concat(cdtTranSign).concat(yDelimit1)
                .concat(cdtTransReference).concat(yDelimit1).concat(cdtCustomer).concat(yDelimit1)
                .concat(cdtAcctOfficer).concat(yDelimit1).concat(cdtProdCateg).concat(yDelimit1).concat(cdtOurRef);

        logger.debug("String leg1 entries = "+ debitLegEntries.concat("#").concat(creditLegEntries).concat("**").concat("Leg1"));
        
        EbCsdAcctEntDetailsRecord acctEntDetsRec = new EbCsdAcctEntDetailsRecord(this);
        acctEntDetsRec.setAccountingEntry(debitLegEntries.concat("#").concat(creditLegEntries).concat("**").concat("Leg1"));
        // Forming Unique Reference Number followed with account number
        String yUniqueId1 = "";
        if (plType.equalsIgnoreCase("Fee")) {
            acctEntDetsRec.setTransConcatDets(feeTransDets);
            yUniqueId1 = UUID.randomUUID().toString() + "*" + plType + "*" + acctNumber;
        } else {
            yUniqueId1 = UUID.randomUUID().toString();
        }
        // Write the details into EB.CSD.ACCT.ENT.DETAILS
        writeAcctEntDets(yUniqueId1, acctEntDetsRec);
        // Is for FCY Currency
        if (!accountingEntry.getAmountFcy().getValue().isBlank()) {
            EbCsdAcctEntDetailsRecord acctEntDetsRecObj = new EbCsdAcctEntDetailsRecord(this);
            String acctEntries2 = getAcctEntries2(exchangeRate, cdtAmount, cdtAccount, accountingEntry,
                    outputVatCalcAmt, plType);
            logger.debug("String acctEntries2 = "+ acctEntries2);
            acctEntDetsRecObj.setAccountingEntry(acctEntries2);
            // Forming Unique Reference Number followed with account number
            writeAcctEntDets(UUID.randomUUID().toString(), acctEntDetsRecObj);
        }
    }

    //
    private boolean checkIncFcy() {
        return (vatCalcType.equalsIgnoreCase(INCLUSIVE) && flagFcy);
    }

    //
    private String getAcctEntries2(String exchangeRate, String cdtAmount, String cdtAccount,
            StmtEntryRecord accountingEntry, String outputVatCalcAmt, String plType) {
        String yDelimit2 = ",";
        // Set the debitLeg for the AcctEntries2
        String yDbtPlCategory = "";
        String yDbtAccount = "";
        String yDbtAmount = "";
        String yDbtCurrency = "";
        String yDbtTranSign = "D";
        String yDbtTransReference = "";
        String yDbtCustomer = "";
        String yDbtAcctOfficer = "";
        String yDbtProdCateg = "";
        String yDbtOurRef = "";
        yDbtAccount = cdtAccount;
        if (plType.equalsIgnoreCase("Fee") || inputVatind) {
            yDbtAmount = getExchangeCurrencyAmt(cdtAmount, exchangeRate);
        } else {
            yDbtAmount = outputVatCalcAmt;
        }
        yDbtCurrency = ySession.getLocalCurrency();
        yDbtTransReference = accountingEntry.getTransReference().getValue();
        yDbtCustomer = accountingEntry.getCustomerId().getValue();
        yDbtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        yDbtProdCateg = accountingEntry.getProductCategory().getValue();
        yDbtOurRef = accountingEntry.getOurReference().getValue();

        // Forming DebitLeg Message
        String yDebitLegEntries = yBulkNm.concat(yDelimit2).concat(yDTransCode).concat(yDelimit2).concat(yDbtPlCategory)
                .concat(yDelimit2).concat(yDbtAccount).concat(yDelimit2).concat(yDbtAmount).concat(yDelimit2)
                .concat(yDbtCurrency).concat(yDelimit2).concat(yDbtTranSign).concat(yDelimit2)
                .concat(yDbtTransReference).concat(yDelimit2).concat(yDbtCustomer).concat(yDelimit2)
                .concat(yDbtAcctOfficer).concat(yDelimit2).concat(yDbtProdCateg).concat(yDelimit2).concat(yDbtOurRef);

        // Set the CreditLeg for the AcctEntries2
        String yCdtPlCategory = "";
        String yCdtAccount = "";
        String yCdtAmount = "";
        String yCdtCurrency = "";
        String yCdtTranSign = "C";
        String yCdtTransReference = "";
        String yCdtCustomer = "";
        String yCdtAcctOfficer = "";
        String yCdtProdCateg = "";
        String yCdtOurRef = "";
        yCdtAccount = getLcyPositioningAcct(yDbtAccount);
        yCdtAmount = yDbtAmount;
        yCdtCurrency = ySession.getLocalCurrency();
        yCdtTransReference = accountingEntry.getTransReference().getValue();
        yCdtCustomer = accountingEntry.getCustomerId().getValue();
        yCdtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        yCdtProdCateg = accountingEntry.getProductCategory().getValue();
        yCdtOurRef = accountingEntry.getOurReference().getValue();
        // Forming CreditLeg Message
        String yCreditLegEntries = yBulkNm.concat(yDelimit2).concat(yCTransCode).concat(yDelimit2)
                .concat(yCdtPlCategory).concat(yDelimit2).concat(yCdtAccount).concat(yDelimit2).concat(yCdtAmount)
                .concat(yDelimit2).concat(yCdtCurrency).concat(yDelimit2).concat(yCdtTranSign).concat(yDelimit2)
                .concat(yCdtTransReference).concat(yDelimit2).concat(yCdtCustomer).concat(yDelimit2)
                .concat(yCdtAcctOfficer).concat(yDelimit2).concat(yCdtProdCateg).concat(yDelimit2).concat(yCdtOurRef);

        return yDebitLegEntries.concat("#").concat(yCreditLegEntries).concat("**").concat("Leg2");
    }

    //
    private String getFcyWotConv(String outputVatCalcAmt, String plType, StmtEntryRecord accountingEntry,
            String exchangeRate) {
        String yFirstLegAmt = "";
        if (!accountingEntry.getAmountFcy().getValue().isBlank() && plType.equalsIgnoreCase(INTEREST) && !inputVatind) {
            BigDecimal finalAmt = new BigDecimal(outputVatCalcAmt);
            BigDecimal outputVatExchangeRate = new BigDecimal(exchangeRate);
            BigDecimal wotFcyConv = finalAmt.divide(outputVatExchangeRate, 10, RoundingMode.HALF_UP).setScale(2,
                    RoundingMode.HALF_UP);
            yFirstLegAmt = wotFcyConv.toString();
        } else {
            yFirstLegAmt = outputVatCalcAmt;
        }
        return yFirstLegAmt;
    }

    //
    private void writeAcctIdDets(String acctNumber, String revEntFlag) {
        try {
            EbCsdAcctNoDetsUpdRecord acctNoDetsUpdRecord = new EbCsdAcctNoDetsUpdRecord(this);
            acctNoDetsUpdRecord.setBestFitCustId(bstFitCustId);
            acctNoDetsUpdRecord.setAcctTaxType(taxType+"-"+revEntFlag);
            EbCsdAcctNoDetsUpdTable acctNoDetsUpdTable = new EbCsdAcctNoDetsUpdTable(this);
            acctNoDetsUpdTable.write(acctNumber, acctNoDetsUpdRecord);
        } catch (Exception e) {
            //
        }
    }

    //
    private void writeAcctEntDets(String yUniqueId, EbCsdAcctEntDetailsRecord acctEntDetsRec) {
        try {
            EbCsdAcctEntDetailsTable acctEntDetsTable = new EbCsdAcctEntDetailsTable(this);
            acctEntDetsTable.write(yUniqueId, acctEntDetsRec);
        } catch (Exception e) {
            //
        }
    }

    //
    private String getvatPayAcct(String vatPayableAcct, StmtEntryRecord accountingEntry) {
        String intAcctNumber = "";
        logger.debug("String ListofInternalAcct from CATEG.INT.ACCT = "+ da.getConcatValues("CATEG.INT.ACCT", vatPayableAcct));
        for (String intAcctNo : da.getConcatValues("CATEG.INT.ACCT", vatPayableAcct)) {
            try {
                AccountRecord yIntAcctRec = new AccountRecord(da
                        .getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "ACCOUNT", "", intAcctNo));
                if (yIntAcctRec.getCurrency().getValue().equals(accountingEntry.getCurrency().getValue())) {
                    intAcctNumber = intAcctNo;
                    logger.debug("String intAcctNumber = "+ intAcctNumber);
                    break;
                }
            } catch (Exception e) {
                //
            }
        }
        return intAcctNumber;
    }

    //
    private String getExchangeRate(CurrencyRecord yCurrency, String vatCurrMar) {
        String yExchangeRate = "";
        for (CurrencyMarketClass currMar : yCurrency.getCurrencyMarket()) {
            if (currMar.getCurrencyMarket().getValue().equals(vatCurrMar)) {
                yExchangeRate = currMar.getMidRevalRate().getValue();
                break;
            }
        }
        return yExchangeRate;
    }

    //
    private void writeDetstoLiveTable(EbCsdTaxDetailsUpdRecord yTaxDetsUpdRec, String acctNumber) {
        try {
            EbCsdTaxDetailsUpdTable yTaxDetsUpdtab = new EbCsdTaxDetailsUpdTable(this);
            yTaxDetsUpdtab.write(acctNumber, yTaxDetsUpdRec);
        } catch (Exception e) {
            //
        }
    }

    //
    private String getAcctNum(StmtEntryRecord accountingEntry) {
        String acctNum = "";
        if (accountingEntry.getSystemId().getValue().equals(arrIden)) {
            logger.debug("*** AA getAccountNumber Condition Satisfied ***");
            acctNum = getAaAcctNum(accountingEntry);
            logger.debug("String acctNum = "+ acctNum);
        }
        if (accountingEntry.getSystemId().getValue().equals(paymentIden)) {
            logger.debug("*** PP getAccountNumber Condition Satisfied ***");
            acctNum = getPorTransactionRec(accountingEntry);
            logger.debug("String acctNum = "+ acctNum);
        }
        if (accountingEntry.getSystemId().getValue().equals(accIden) ||
                accountingEntry.getSystemId().getValue().equals(ftIden)) {
            logger.debug("*** AC/FT getAccountNumber Condition Satisfied ***");
            acctNum = accountingEntry.getTransReference().getValue();
            logger.debug("String acctNum = "+ acctNum);
        }
        return acctNum;
    }

    //
    private String getAaAcctNum(StmtEntryRecord accountingEntry) {
        String acctNumber = "";
        String outRef = accountingEntry.getOurReference().getValue();
        if (outRef.startsWith("AA")) {
            AaArrangementRecord yArrRec = new AaArrangementRecord(da.getRecord("AA.ARRANGEMENT", outRef));
            acctNumber = yArrRec.getLinkedAppl(0).getLinkedApplId().getValue();
        } else {
            acctNumber = outRef;
        }
        return acctNumber;
    }

    //
    private String getPorTransactionRec(StmtEntryRecord accountingEntry) {
        String yAcctNum = "";
        try {
            PorTransactionRecord yPorTransRec = new PorTransactionRecord(
                    da.getRecord(POR_TRANSACTION, accountingEntry.getTransReference().getValue()));
            yAcctNum = getAcctNumber(yPorTransRec, accountingEntry);
            /**yAcctNum = yPorTransRec.getDebitmainaccount().getValue();
            boolean ckhPlAcctFlag = chkPlCategAcct(yPorTransRec);
            if (ckhPlAcctFlag) {
                yAcctNum = getAcctNumber(yPorTransRec, accountingEntry);
            }*/
        } catch (Exception e) {
            logger.debug("Exception at getAccount from PORTRANS = "+ e.toString());
        }
        return yAcctNum;
    }

    private String getAcctNumber(PorTransactionRecord yPorTransRec, StmtEntryRecord accountingEntry) {
        String accountNo = "";
        // Get the account number for direct PL Account involved in transaction
        if (yPorTransRec.getDebitmainaccount().getValue().startsWith(plCategEntMne)
                && !yPorTransRec.getCreditmainaccount().getValue().startsWith(plCategEntMne)) {
            accountNo = yPorTransRec.getCreditmainaccount().getValue();
        } else if (!yPorTransRec.getDebitmainaccount().getValue().startsWith(plCategEntMne)
                && yPorTransRec.getCreditmainaccount().getValue().startsWith(plCategEntMne)) {
            accountNo = yPorTransRec.getDebitmainaccount().getValue();
        }
        // Get the account number for LCY transaction both Success and reversal
        if (accountNo.isBlank() && accountingEntry.getAmountFcy().getValue().isBlank()
                && !accountingEntry.getAmountLcy().getValue().startsWith("-")) {
            accountNo = yPorTransRec.getDebitmainaccount().getValue();
        } else if (accountNo.isBlank() && accountingEntry.getAmountFcy().getValue().isBlank()
                && accountingEntry.getAmountLcy().getValue().startsWith("-")) {
            accountNo = yPorTransRec.getCreditmainaccount().getValue();
        }
        // Get the account number for FCY transaction both Success and reversal
        if (accountNo.isBlank() && !accountingEntry.getAmountFcy().getValue().isBlank()
                && !accountingEntry.getAmountLcy().getValue().startsWith("-")) {
            accountNo = yPorTransRec.getDebitmainaccount().getValue();
        } else if (accountNo.isBlank() && !accountingEntry.getAmountFcy().getValue().isBlank()
                && accountingEntry.getAmountLcy().getValue().startsWith("-")) {
            accountNo = yPorTransRec.getCreditmainaccount().getValue();
        }
        return accountNo;
    }

    /**private boolean chkPlCategAcct(PorTransactionRecord yPorTransRec) {
        boolean yAcctFlag = false;
        if (yPorTransRec.getDebitmainaccount().getValue().startsWith(plCategEntMne)) {
            yAcctFlag = true;
        }
        return yAcctFlag;
    }*/

    //
    private InterestDetailClass setInterestTransDets(StmtEntryRecord accountingEntry, InterestDetailClass yIntDets,
            String taxRateVal, String exchangeRate) {
        if (!accountingEntry.getAmountLcy().getValue().isBlank() && accountingEntry.getAmountFcy().getValue().isBlank()
                && !accountingEntry.getAmountLcy().getValue().startsWith("-")) {
            yIntDets.setInterestTransactionType(yIncome);
            yIntDets.setOutputVatRateForInterest(taxRateVal);
        } else if (!accountingEntry.getAmountFcy().getValue().isBlank()
                && !accountingEntry.getAmountLcy().getValue().startsWith("-")) {
            yIntDets.setInterestTransactionType(yIncome);
            yIntDets.setOutputVatRateForInterest(taxRateVal);
            yIntDets.setVatExchangeRateForInterest(exchangeRate);
        }
        return yIntDets;
    }

    //
    public String updTotalIntDets(EbCsdTaxDetailsUpdRecord yTaxDetsUpdRec, StmtEntryRecord accountingEntry, AccountRecord yAcctRec, EbCsdVatWhtRateParamRecord yVatWhtParamRec, int bestFitIndex, String acctNumber) {
        String taxTypeCond = yVatWhtParamRec.getClientCategory(bestFitIndex).getVatRate().getValue();
        String taxcde = fetchTaxCode(taxTypeCond);
        String taxRateVal = fetchTaxRate(taxcde);
        logger.debug("String taxRateVal = "+ taxRateVal);
        // Calculate the Output VAT amount for the Transaction amount
        String yOutputVatCalcAmt = calculateOutputVatAmt(accountingEntry, taxRateVal);
        logger.debug("String First yOutputVatCalcAmt from Categ entry = "+ yOutputVatCalcAmt);
        // Calculate then Exchange Rate when the Foreign Transaction Currency is happened
        String yExchangeRate = calculateExhangeRate(accountingEntry);
        logger.debug("String yExchangeRate = "+ yExchangeRate);
        // Get the Interest Property
        String intPropId = getInterestProp(accountingEntry, yAcctRec);
        // Update the total interest details from the AA.INTEREST.ACCRUAL
        String yIntAccId = yAcctRec.getArrangementId().getValue().concat("-").concat(intPropId);
        logger.debug("String yIntAccId = "+ yIntAccId);
        // Read Interest Accruals table
        AaInterestAccrualsRecord yIntAccRec = new AaInterestAccrualsRecord(
                da.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(),
                        "AA.INTEREST.ACCRUALS", "", yIntAccId));
        int periodStartLen = yIntAccRec.getPeriodStart().size();
        logger.debug("String periodStartLen = "+ periodStartLen);
        if (periodStartLen == 1) {
            // If Interest Details set is empty then updated the interest details below.
            if (yTaxDetsUpdRec.getInterestDetail().size() == 0) {
                InterestDetailClass yIntDets = new InterestDetailClass();
                PeriodStartClass yPeriodStartObj = new PeriodStartClass();
                yIntDets.setInterestDetail(intPropId);
                yIntDets.setInterestCurrency(accountingEntry.getCurrency().getValue());
                // Update the Interest Transaction Type
                setInterestTransDets(accountingEntry, yIntDets, taxRateVal, yExchangeRate);
                yPeriodStartObj.setPeriodStart(yIntAccRec.getPeriodStart(periodStartLen-1).getPeriodStart().getValue());
                yPeriodStartObj.setPeriodEnd(yIntAccRec.getPeriodStart(periodStartLen-1).getPeriodEnd().getValue());
                yPeriodStartObj.setTotalIntAmount(yIntAccRec.getPeriodStart(periodStartLen-1).getTotAccrAmt().getValue());
                // Update the Interest Transaction Type
                yOutputVatCalcAmt = updateOutVatAmt(accountingEntry, yPeriodStartObj, yOutputVatCalcAmt, yExchangeRate);
                // Set the Period Start class in the first position
                yIntDets.setPeriodStart(yPeriodStartObj, 0);
                yTaxDetsUpdRec.setInterestDetail(yIntDets, 0);
                logger.debug("String yTaxDetsUpdRec = "+ yTaxDetsUpdRec);
            // If Interest Details set is not empty then updated the interest details accordingly.
            } else if (yTaxDetsUpdRec.getInterestDetail().size() == periodStartLen){
                String intDetail = yTaxDetsUpdRec.getInterestDetail(0).getInterestDetail().getValue();
                // Update the totalOutputVatmt only of the same interest is already available from live table
                if (intDetail.equals(intPropId)) {
                    // Get the Total InterestAccrualAmount from AA.INTEREST.ACCRUALS
                    String totalIntAccAmt = yIntAccRec.getPeriodStart(periodStartLen-1).getTotAccrAmt().getValue();
                    logger.debug("String totalIntAccAmt = "+ totalIntAccAmt);
                    // Calculating the OutputVat Amount for the total InterestAccrualAmount
                    yOutputVatCalcAmt = calculateTaxAmt(totalIntAccAmt, taxRateVal);
                    logger.debug("String yOutputVatCalcAmt from Accrual table = "+ yOutputVatCalcAmt);
                    yOutputVatCalcAmt = calTotalIntAmt(yTaxDetsUpdRec, yOutputVatCalcAmt, yExchangeRate, accountingEntry, totalIntAccAmt);
                    logger.debug("String yOutputVatCalcAmt after calcuated total output VAT FCY = "+ yOutputVatCalcAmt);
                    logger.debug("String yTaxDetsUpdRec = "+ yTaxDetsUpdRec);
                } else {
                    InterestDetailClass yInterestDets = new InterestDetailClass();
                    PeriodStartClass yPerStaObj = new PeriodStartClass();
                    yInterestDets.setInterestDetail(intPropId);
                    yInterestDets.setInterestCurrency(accountingEntry.getCurrency().getValue());
                    // Update the Interest Transaction Type
                    setInterestTransDets(accountingEntry, yInterestDets, taxRateVal, yExchangeRate);
                    yPerStaObj.setPeriodStart(yIntAccRec.getPeriodStart(periodStartLen-1).getPeriodStart().getValue());
                    yPerStaObj.setPeriodEnd(yIntAccRec.getPeriodStart(periodStartLen-1).getPeriodEnd().getValue());
                    yPerStaObj.setTotalIntAmount(yIntAccRec.getPeriodStart(periodStartLen-1).getTotAccrAmt().getValue());
                    logger.debug("String yOutputVatCalcAmt = "+ yOutputVatCalcAmt);
                    // Update the Interest Transaction Type
                    yOutputVatCalcAmt = updateOutVatAmt(accountingEntry, yPerStaObj, yOutputVatCalcAmt, yExchangeRate);
                    // Set the Period Start class in the first position
                    yInterestDets.setPeriodStart(yPerStaObj, yInterestDets.getPeriodStart().size());
                    logger.debug("String yInterestDets = "+ yInterestDets);
                    yTaxDetsUpdRec.setInterestDetail(yInterestDets, yTaxDetsUpdRec.getInterestDetail().size());
                    logger.debug("String yTaxDetsUpdRec = "+ yTaxDetsUpdRec);
                }
            }
        } else {
            for (InterestDetailClass yIntDetsClass : yTaxDetsUpdRec.getInterestDetail()) {
                if (yIntDetsClass.getInterestDetail().getValue().equals(intPropId)
                        && periodStartLen > yIntDetsClass.getPeriodStart().size()) {
                    // Get the Total InterestAccrualAmount from AA.INTEREST.ACCRUALS
                    String ytotalIntAccAmt = yIntAccRec.getPeriodStart(periodStartLen-1).getTotAccrAmt().getValue();
                    logger.debug("String ytotalIntAccAmt = "+ ytotalIntAccAmt);
                    // Calculating the OutputVat Amount for the total InterestAccrualAmount
                    yOutputVatCalcAmt = calculateTaxAmt(ytotalIntAccAmt, taxRateVal);
                    logger.debug("String yOutputVatCalcAmt output VAT FCY = "+ yOutputVatCalcAmt);
                    yOutputVatCalcAmt = updNewPerStartSet(yOutputVatCalcAmt, yExchangeRate, accountingEntry, ytotalIntAccAmt, yIntDetsClass, yIntAccRec, periodStartLen);
                    logger.debug("String yTaxDetsUpdRec = "+ yTaxDetsUpdRec);
                } else if (periodStartLen == yIntDetsClass.getPeriodStart().size()){
                    // Get the Total InterestAccrualAmount from AA.INTEREST.ACCRUALS
                    String totalIntAccAmt = yIntAccRec.getPeriodStart(periodStartLen-1).getTotAccrAmt().getValue();
                    logger.debug("String totalIntAccAmt = "+ totalIntAccAmt);
                    // Calculating the OutputVat Amount for the total InterestAccrualAmount
                    yOutputVatCalcAmt = calculateTaxAmt(totalIntAccAmt, taxRateVal);
                    logger.debug("String yOutputVatCalcAmt output VAT FCY = "+ yOutputVatCalcAmt);
                    yOutputVatCalcAmt = updExtTotVatIntAmt( yOutputVatCalcAmt, yExchangeRate, accountingEntry, totalIntAccAmt, yIntDetsClass);
                    logger.debug("String yTaxDetsUpdRec = "+ yTaxDetsUpdRec);
                }
            }
        }
        // Write the Transaction details into Live Table
        writeDetstoLiveTable(yTaxDetsUpdRec, acctNumber);
        // Update the accounting entries details into EB.CSD.ACCT.ENT.DETAILS.EOD
        updAcctEntriesDetsEod(accountingEntry, yOutputVatCalcAmt, yVatWhtParamRec, yExchangeRate);
        
        return yOutputVatCalcAmt;
    }

    //
    private void updAcctEntriesDetsEod(StmtEntryRecord accountingEntry, String yOutputVatCalcAmt,
            EbCsdVatWhtRateParamRecord yVatWhtParamRec, String yExchangeRate) {
        // get AC.ENTRY.PARAM record
        AcEntryParamRecord acEntParamRec = getAcParamRec(da);
        String yDelimit1 = "*";
        // Set the debit Leg
        String dbtAccount = "";
        String dbtCompanyCode = "";
        String dbtAmountLcy = "";
        String dbtTransCode = "";
        String dbtPlCategory = "";
        String dbtCustomerId = "";
        String dbtAcctOfficer = "";
        String dbtProdCateg = "";
        String dbtValueDate = "";
        String dbtCurrency = "";
        String dbtAmountFcy = "";
        String dbtExchgRte = "";
        String dbtPositionType = "";
        String dbtOutRef = "";
        String dbtExpoDate = "";
        String dbtCcyMkt = "";
        String dbtTransReference = "";
        String dbtSysId = "";
        String dbtBookingDte = "";

        dbtCompanyCode = accountingEntry.getCompanyCode().getValue(); 
        dbtTransCode = acEntParamRec.getDrTxnCode().getValue();
        dbtPlCategory = accountingEntry.getPlCategory().getValue();
        dbtCustomerId = accountingEntry.getCustomerId().getValue();
        dbtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        dbtProdCateg = accountingEntry.getProductCategory().getValue();
        dbtValueDate = accountingEntry.getValueDate().getValue();
        dbtCurrency = accountingEntry.getCurrency().getValue();
        
        if (!accountingEntry.getAmountFcy().getValue().isBlank()) {
            dbtAmountFcy = yOutputVatCalcAmt;
            dbtExchgRte = getDefExchgRate(accountingEntry.getCurrency().getValue());
            dbtAmountLcy = getCalcAmtLcy(dbtAmountFcy, dbtExchgRte);
        } else {
            dbtAmountLcy = yOutputVatCalcAmt;
        }
        
        dbtPositionType = accountingEntry.getPositionType().getValue();
        dbtOutRef = accountingEntry.getOurReference().getValue();
        dbtExpoDate = accountingEntry.getExposureDate().getValue();
        dbtCcyMkt = accountingEntry.getCurrencyMarket().getValue();
        dbtTransReference = accountingEntry.getTransReference().getValue();
        dbtSysId = accountingEntry.getSystemId().getValue();
        dbtBookingDte = accountingEntry.getBookingDate().getValue();
        
        // Forming DebitLeg Message
        String debitLegEntries = dbtAccount.concat(yDelimit1).concat(dbtCompanyCode).concat(yDelimit1)
                .concat(dbtAmountLcy).concat(yDelimit1).concat(dbtTransCode).concat(yDelimit1).concat(dbtPlCategory)
                .concat(yDelimit1).concat(dbtCustomerId).concat(yDelimit1).concat(dbtAcctOfficer).concat(yDelimit1)
                .concat(dbtProdCateg).concat(yDelimit1).concat(dbtValueDate).concat(yDelimit1).concat(dbtCurrency)
                .concat(yDelimit1).concat(dbtAmountFcy).concat(yDelimit1).concat(dbtExchgRte).concat(yDelimit1)
                .concat(dbtPositionType).concat(yDelimit1).concat(dbtOutRef).concat(yDelimit1).concat(dbtExpoDate)
                .concat(yDelimit1).concat(dbtCcyMkt).concat(yDelimit1).concat(dbtTransReference).concat(yDelimit1)
                .concat(dbtSysId).concat(yDelimit1).concat(dbtBookingDte);
        logger.debug("String debitLegEntries = "+ debitLegEntries);
        
        // Set the CreditLeg for the AcctEntries1
        String cdtAccount = "";
        String cdtCompanyCode = "";
        String cdtAmountLcy = "";
        String cdtTransCode = "";
        String cdtPlCategory = "";
        String cdtCustomerId = "";
        String cdtAcctOfficer = "";
        String cdtProdCateg = "";
        String cdtValueDate = "";
        String cdtCurrency = "";
        String cdtAmountFcy = "";
        String cdtExchgRte = "";
        String cdtPositionType = "";
        String cdtOutRef = "";
        String cdtExpoDate = "";
        String cdtCcyMkt = "";
        String cdtTransReference = "";
        String cdtSysId = "";
        String cdtBookingDte = "";
        
        cdtAccount = getvatPayAcct(yVatWhtParamRec.getVatPayableAccount().getValue(), accountingEntry);
        cdtCompanyCode = accountingEntry.getCompanyCode().getValue(); 
        cdtTransCode = acEntParamRec.getCrTxnCode().getValue();;
        cdtCustomerId = accountingEntry.getCustomerId().getValue();
        cdtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        cdtProdCateg = accountingEntry.getProductCategory().getValue();
        cdtValueDate = accountingEntry.getValueDate().getValue();
        cdtCurrency = accountingEntry.getCurrency().getValue();
        
        if (!accountingEntry.getAmountFcy().getValue().isBlank()) {
            cdtAmountFcy = dbtAmountFcy;
            cdtExchgRte = dbtExchgRte;
            cdtAmountLcy = dbtAmountLcy;
        } else {
            cdtAmountLcy = dbtAmountLcy;
        }
        
        cdtPositionType = accountingEntry.getPositionType().getValue();
        cdtOutRef = accountingEntry.getOurReference().getValue();
        cdtExpoDate = accountingEntry.getExposureDate().getValue();
        cdtCcyMkt = accountingEntry.getCurrencyMarket().getValue();
        cdtTransReference = accountingEntry.getTransReference().getValue();
        cdtSysId = accountingEntry.getSystemId().getValue();
        cdtBookingDte = accountingEntry.getBookingDate().getValue();
        
        // Forming CreditLeg Message
        String creditLegEntries = cdtAccount.concat(yDelimit1).concat(cdtCompanyCode).concat(yDelimit1)
                .concat(cdtAmountLcy).concat(yDelimit1).concat(cdtTransCode).concat(yDelimit1).concat(cdtPlCategory)
                .concat(yDelimit1).concat(cdtCustomerId).concat(yDelimit1).concat(cdtAcctOfficer).concat(yDelimit1)
                .concat(cdtProdCateg).concat(yDelimit1).concat(cdtValueDate).concat(yDelimit1).concat(cdtCurrency)
                .concat(yDelimit1).concat(cdtAmountFcy).concat(yDelimit1).concat(cdtExchgRte).concat(yDelimit1)
                .concat(cdtPositionType).concat(yDelimit1).concat(cdtOutRef).concat(yDelimit1).concat(cdtExpoDate)
                .concat(yDelimit1).concat(cdtCcyMkt).concat(yDelimit1).concat(cdtTransReference).concat(yDelimit1)
                .concat(cdtSysId).concat(yDelimit1).concat(cdtBookingDte);
        logger.debug("String creditLegEntries = "+ creditLegEntries);
        
        EbCsdAcctEntDetailsEodRecord acctEntDetsRecEodRec = new EbCsdAcctEntDetailsEodRecord(this);
        logger.debug("String before set acctEntDetsRecEodRec = "+ acctEntDetsRecEodRec);
        acctEntDetsRecEodRec.setAccountingEntry(debitLegEntries.concat("$$").concat(creditLegEntries));
        logger.debug("String after set acctEntDetsRecEodRec = "+ acctEntDetsRecEodRec);
        // Forming Unique Reference Number followed with account number
        String yUniqueId1 = UUID.randomUUID().toString();
        logger.debug("String yUniqueId1 leg1 = "+ yUniqueId1);
        // Write the details into EB.CSD.ACCT.ENT.DETAILS.EOD
        writeAcctEntDetsEod(yUniqueId1, acctEntDetsRecEodRec);
        
        // Is for FCY Currency
        if (!accountingEntry.getAmountFcy().getValue().isBlank()) {
            EbCsdAcctEntDetailsEodRecord acctEntDetsEodRecObj = new EbCsdAcctEntDetailsEodRecord(this);
            String acctEntriesLeg2 = getAcctEntriesLeg2(yExchangeRate, cdtAccount, accountingEntry,
                    acEntParamRec, cdtCurrency, cdtAmountFcy);
            acctEntDetsEodRecObj.setAccountingEntry(acctEntriesLeg2);
            logger.debug("String acctEntDetsEodRecObj leg2 = "+ acctEntDetsEodRecObj);
            // Write the details into EB.CSD.ACCT.ENT.DETAILS.EOD
            writeAcctEntDetsEod(UUID.randomUUID().toString(), acctEntDetsEodRecObj);
        }
    }

    //
    private String getCalcAmtLcy(String dbtAmountFcy, String dbtExchgRte) {
        BigDecimal calcAmtLcy = BigDecimal.ZERO;
        try {
            calcAmtLcy = new BigDecimal(dbtAmountFcy).multiply(
                    new BigDecimal(dbtExchgRte)).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            // 
        }
        return calcAmtLcy.toString();
    }

    //
    private String getDefExchgRate(String currency) {
        String defMidRevRate = "";
        try {
            CurrencyRecord yCcyRec = new CurrencyRecord(da.getRecord("CURRENCY", currency));
            for (CurrencyMarketClass ccyMkt : yCcyRec.getCurrencyMarket()) {
                if (ccyMkt.getCurrencyMarket().getValue().equals("1")) {
                    defMidRevRate = ccyMkt.getMidRevalRate().getValue();
                    break;
                }
            }
        } catch (Exception e) {
            // 
        }
        return defMidRevRate;
    }

    //
    private AcEntryParamRecord getAcParamRec(DataAccess da2) {
        AcEntryParamRecord acEntryParamRec = new AcEntryParamRecord();
        try {
            EbCsdTaxDetsParamRecord taxDetsPrmRec = new EbCsdTaxDetsParamRecord(da.getRecord(
                    ySession.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.TAX.DETS.PARAM", "", ySystemId));
            String paramId = "";
            if (whtInd) {
                paramId = taxDetsPrmRec.getWhtEntryParam().getValue();
            } else {
                paramId = taxDetsPrmRec.getVatEntryParam().getValue();
            }
            acEntryParamRec = new AcEntryParamRecord(da.getRecord("AC.ENTRY.PARAM", paramId));
        } catch (Exception e) {
            //
        }
        return acEntryParamRec;
    }

    //
    private String getAcctEntriesLeg2(String yExchangeRate, String cdtAccount, StmtEntryRecord accountingEntry,
            AcEntryParamRecord acEntParamRec, String cdtCurrency, String cdtAmountFcy) {
        String yDelimit2 = "*";
        // Set the debit Leg
        String ydbtAccount = "";
        String ydbtCompanyCode = "";
        String ydbtAmountLcy = "";
        String ydbtTransCode = "";
        String ydbtPlCategory = "";
        String ydbtCustomerId = "";
        String ydbtAcctOfficer = "";
        String ydbtProdCateg = "";
        String ydbtValueDate = "";
        String ydbtCurrency = "";
        String ydbtAmountFcy = "";
        String ydbtExchgRte = "";
        String ydbtPositionType = "";
        String ydbtOutRef = "";
        String ydbtExpoDate = "";
        String ydbtCcyMkt = "";
        String ydbtTransReference = "";
        String ydbtSysId = "";
        String ydbtBookingDte = "";
        
        ydbtAccount = cdtAccount;
        ydbtCompanyCode = accountingEntry.getCompanyCode().getValue(); 
        ydbtAmountLcy = getCalcAmtLcy(cdtAmountFcy, yExchangeRate);
        ydbtTransCode = acEntParamRec.getDrTxnCode().getValue();
        ydbtCustomerId = accountingEntry.getCustomerId().getValue();
        ydbtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        ydbtProdCateg = accountingEntry.getProductCategory().getValue();
        ydbtValueDate = accountingEntry.getValueDate().getValue();
        ydbtCurrency = cdtCurrency;
        ydbtAmountFcy = cdtAmountFcy;
        ydbtExchgRte = yExchangeRate;
        ydbtPositionType = accountingEntry.getPositionType().getValue();
        ydbtOutRef = accountingEntry.getOurReference().getValue();
        ydbtExpoDate = accountingEntry.getExposureDate().getValue();
        ydbtCcyMkt = accountingEntry.getCurrencyMarket().getValue();
        ydbtTransReference = accountingEntry.getTransReference().getValue();
        ydbtSysId = accountingEntry.getSystemId().getValue();
        ydbtBookingDte = accountingEntry.getBookingDate().getValue();
        
        String yDebitLegEntries = ydbtAccount.concat(yDelimit2).concat(ydbtCompanyCode).concat(yDelimit2)
                .concat(ydbtAmountLcy).concat(yDelimit2).concat(ydbtTransCode).concat(yDelimit2).concat(ydbtPlCategory)
                .concat(yDelimit2).concat(ydbtCustomerId).concat(yDelimit2).concat(ydbtAcctOfficer).concat(yDelimit2)
                .concat(ydbtProdCateg).concat(yDelimit2).concat(ydbtValueDate).concat(yDelimit2).concat(ydbtCurrency)
                .concat(yDelimit2).concat(ydbtAmountFcy).concat(yDelimit2).concat(ydbtExchgRte).concat(yDelimit2)
                .concat(ydbtPositionType).concat(yDelimit2).concat(ydbtOutRef).concat(yDelimit2).concat(ydbtExpoDate)
                .concat(yDelimit2).concat(ydbtCcyMkt).concat(yDelimit2).concat(ydbtTransReference).concat(yDelimit2)
                .concat(ydbtSysId).concat(yDelimit2).concat(ydbtBookingDte).concat(yDelimit2).concat(getCurrMarketVal(da));
        logger.debug("String yDebitLegEntries = "+ yDebitLegEntries);
        
        // Set the CreditLeg for the AcctEntries1
        String ycdtAccount = "";
        String ycdtCompanyCode = "";
        String ycdtAmountLcy = "";
        String ycdtTransCode = "";
        String ycdtPlCategory = "";
        String ycdtCustomerId = "";
        String ycdtAcctOfficer = "";
        String ycdtProdCateg = "";
        String ycdtValueDate = "";
        String ycdtCurrency = "";
        String ycdtAmountFcy = "";
        String ycdtExchgRte = "";
        String ycdtPositionType = "";
        String ycdtOutRef = "";
        String ycdtExpoDate = "";
        String ycdtCcyMkt = "";
        String ycdtTransReference = "";
        String ycdtSysId = "";
        String ycdtBookingDte = "";
        
        ycdtAccount = getLcyPositioningAcct(ydbtAccount);
        ycdtCompanyCode = accountingEntry.getCompanyCode().getValue(); 
        ycdtAmountLcy = ydbtAmountLcy;
        ycdtTransCode = acEntParamRec.getCrTxnCode().getValue();
        ycdtCustomerId = accountingEntry.getCustomerId().getValue();
        ycdtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        ycdtProdCateg = accountingEntry.getProductCategory().getValue();
        ycdtValueDate = accountingEntry.getValueDate().getValue();
        ycdtCurrency = ySession.getLocalCurrency();
        ycdtPositionType = accountingEntry.getPositionType().getValue();
        ycdtOutRef = accountingEntry.getOurReference().getValue();
        ycdtExpoDate = accountingEntry.getExposureDate().getValue();
        ycdtCcyMkt = accountingEntry.getCurrencyMarket().getValue();
        ycdtTransReference = accountingEntry.getTransReference().getValue();
        ycdtSysId = accountingEntry.getSystemId().getValue();
        ycdtBookingDte = accountingEntry.getBookingDate().getValue();
        
        String yCreditLegEntries = ycdtAccount.concat(yDelimit2).concat(ycdtCompanyCode).concat(yDelimit2)
                .concat(ycdtAmountLcy).concat(yDelimit2).concat(ycdtTransCode).concat(yDelimit2).concat(ycdtPlCategory)
                .concat(yDelimit2).concat(ycdtCustomerId).concat(yDelimit2).concat(ycdtAcctOfficer).concat(yDelimit2)
                .concat(ycdtProdCateg).concat(yDelimit2).concat(ycdtValueDate).concat(yDelimit2).concat(ycdtCurrency)
                .concat(yDelimit2).concat(ycdtAmountFcy).concat(yDelimit2).concat(ycdtExchgRte).concat(yDelimit2)
                .concat(ycdtPositionType).concat(yDelimit2).concat(ycdtOutRef).concat(yDelimit2).concat(ycdtExpoDate)
                .concat(yDelimit2).concat(ycdtCcyMkt).concat(yDelimit2).concat(ycdtTransReference).concat(yDelimit2)
                .concat(ycdtSysId).concat(yDelimit2).concat(ycdtBookingDte).concat(yDelimit2).concat(getCurrMarketVal(da));
        logger.debug("String yCreditLegEntries = "+ yCreditLegEntries);
        
        return yDebitLegEntries.concat("$$").concat(yCreditLegEntries);
    }

    //
    private void writeAcctEntDetsEod(String yUniqueId1, EbCsdAcctEntDetailsEodRecord acctEntDetsRecEodRec) {
        try {
            EbCsdAcctEntDetailsEodTable acctEntDetsTable = new EbCsdAcctEntDetailsEodTable(this);
            acctEntDetsTable.write(yUniqueId1, acctEntDetsRecEodRec);
            logger.debug("String acctEntDetsTable = "+ acctEntDetsTable);
        } catch (Exception e) {
            logger.debug("Error Message at new Table write = "+ e.toString());
        }
    }

    //
    private String updExtTotVatIntAmt(String yOutputVatCalcAmt,
            String yExchangeRate, StmtEntryRecord accountingEntry, String totalIntAccAmt, InterestDetailClass yIntDetsClass) {
        int periodDateSize = yIntDetsClass.getPeriodStart().size();
        PeriodStartClass yCurrPerStart = yIntDetsClass.getPeriodStart(periodDateSize-1);
        String preOutVatAmt = yCurrPerStart.getPreTotOutVatAmt().getValue(); // get the Previous calculated VAT amount
        
        PeriodStartClass yLatPerStart = new PeriodStartClass();
        // Set the Current total interestAccrualAmt
        yLatPerStart.setTotalIntAmount(totalIntAccAmt);
        yLatPerStart.setPeriodStart(yCurrPerStart.getPeriodStart());
        yLatPerStart.setPeriodEnd(yCurrPerStart.getPeriodEnd());
        if (accountingEntry.getAmountFcy().getValue().isBlank()) {
            yLatPerStart.setPreTotOutVatAmt(yOutputVatCalcAmt);
            // Set the Total Output vatLcy Amount for Interest
            yOutputVatCalcAmt = performTotalVat(preOutVatAmt, yOutputVatCalcAmt);
            yLatPerStart.setTotOutVatLcyAmtForInt(yOutputVatCalcAmt);
            yIntDetsClass.setPeriodStart(yLatPerStart, periodDateSize-1);
        } else {
            yLatPerStart.setPreTotOutVatAmt(yOutputVatCalcAmt);
            // Set the Total Output vatFcy Amount for Interest
            yOutputVatCalcAmt = performTotalVat(preOutVatAmt, yOutputVatCalcAmt);
            logger.debug("String outputVatCalcAmt "+yOutputVatCalcAmt+"-"+preOutVatAmt+" = "+yOutputVatCalcAmt);
            yLatPerStart.setTotOutVatFcyAmtForInt(yOutputVatCalcAmt);
            yLatPerStart.setTotOutVatLcyAmtForInt(getExchangeCurrencyAmt(yOutputVatCalcAmt, yExchangeRate));
            logger.debug("String yLatPerStart = "+yLatPerStart);
            yIntDetsClass.setPeriodStart(yLatPerStart, periodDateSize-1);
            logger.debug("String yIntDetsClass = "+yIntDetsClass);
        }
        return yOutputVatCalcAmt;
    }
    
    //
    public String updNewPerStartSet(String yOutputVatCalcAmt,
            String yExchangeRate, StmtEntryRecord accountingEntry, String ytotalIntAccAmt,
            InterestDetailClass exitingIntDets, AaInterestAccrualsRecord yIntAccRec, int periodStartLen) {
        try {
            PeriodStartClass yLatPerStrtObj = new PeriodStartClass();
            // Set the Current total interestAccrualAmt
            yLatPerStrtObj.setTotalIntAmount(ytotalIntAccAmt);
            yLatPerStrtObj.setPeriodStart(yIntAccRec.getPeriodStart(periodStartLen-1).getPeriodStart());
            yLatPerStrtObj.setPeriodEnd(yIntAccRec.getPeriodStart(periodStartLen-1).getPeriodEnd());
            
            if (accountingEntry.getAmountFcy().getValue().isBlank()) {
                yLatPerStrtObj.setPreTotOutVatAmt(yOutputVatCalcAmt);
                // Set the Total Output vatLcy Amount for Interest
                yLatPerStrtObj.setTotOutVatLcyAmtForInt(yOutputVatCalcAmt);
                exitingIntDets.setPeriodStart(yLatPerStrtObj, exitingIntDets.getPeriodStart().size());
            } else {
                yLatPerStrtObj.setPreTotOutVatAmt(yOutputVatCalcAmt);
                // Set the Total Output vatFcy Amount for Interest
                yLatPerStrtObj.setTotOutVatFcyAmtForInt(yOutputVatCalcAmt);
                yLatPerStrtObj.setTotOutVatLcyAmtForInt(getExchangeCurrencyAmt(yOutputVatCalcAmt, yExchangeRate));
                exitingIntDets.setPeriodStart(yLatPerStrtObj, exitingIntDets.getPeriodStart().size());
                logger.debug("String exitingIntDets = "+ exitingIntDets);
                /**String outVatFcyAmtForInt = getExchangeCurrencyAmt(yOutputVatCalcAmt, yExchangeRate);
                yOutputVatCalcAmt = outVatFcyAmtForInt;
                yLatPerStrtObj.setTotOutVatFcyAmtForInt(outVatFcyAmtForInt);*/
            }
        } catch (Exception e) {
            // 
        }
        return yOutputVatCalcAmt;
    }

    //
    private String calTotalIntAmt(EbCsdTaxDetailsUpdRecord yTaxDetsUpdRec, String outputVatCalcAmt,
            String exchangeRate, StmtEntryRecord accountingEntry, String totalIntAccAmt) {
        try {
            int currIntDets = yTaxDetsUpdRec.getInterestDetail().size()-1; // get the size of the current InterestDetails
            InterestDetailClass preIntDets = yTaxDetsUpdRec.getInterestDetail(currIntDets); // get the current InterestDetails
            int currPerStat = yTaxDetsUpdRec.getInterestDetail(currIntDets).getPeriodStart().size()-1; // get the size of the current PeriodStart
            
            PeriodStartClass yCurrPerStart = yTaxDetsUpdRec.
                    getInterestDetail(currIntDets).getPeriodStart(currPerStat); // get the current PeriodStart
            String preOutVatAmt = yCurrPerStart.getPreTotOutVatAmt().getValue(); // get the Previous calculated VAT amount
            
            PeriodStartClass yLatPerStart = new PeriodStartClass();
            // Set the Current total interestAccrualAmt
            yLatPerStart.setTotalIntAmount(totalIntAccAmt);
            yLatPerStart.setPeriodStart(yCurrPerStart.getPeriodStart());
            yLatPerStart.setPeriodEnd(yCurrPerStart.getPeriodEnd());
            if (accountingEntry.getAmountFcy().getValue().isBlank()) {
                yLatPerStart.setPreTotOutVatAmt(outputVatCalcAmt);
                // Set the Total Output vatLcy Amount for Interest
                outputVatCalcAmt = performTotalVat(preOutVatAmt, outputVatCalcAmt);
                yLatPerStart.setTotOutVatLcyAmtForInt(outputVatCalcAmt);
                preIntDets.setPeriodStart(yLatPerStart, yTaxDetsUpdRec.getInterestDetail(0).getPeriodStart().size()-1);
            } else {
                yLatPerStart.setPreTotOutVatAmt(outputVatCalcAmt);
                logger.debug("String yLatPerStart - start = "+ yLatPerStart);
                // Set the Total Output vatFcy Amount for Interest
                outputVatCalcAmt = performTotalVat(preOutVatAmt, outputVatCalcAmt);
                logger.debug("String outputVatCalcAmt "+outputVatCalcAmt+"-"+preOutVatAmt+" = "+outputVatCalcAmt);
                yLatPerStart.setTotOutVatFcyAmtForInt(outputVatCalcAmt);
                yLatPerStart.setTotOutVatLcyAmtForInt(getExchangeCurrencyAmt(outputVatCalcAmt, exchangeRate));
                preIntDets.setPeriodStart(yLatPerStart, yTaxDetsUpdRec.getInterestDetail(currIntDets).getPeriodStart().size()-1);
                logger.debug("String yLatPerStart - end = "+ yLatPerStart);
                /**String outVatFcyAmtForInt = getExchangeCurrencyAmt(outputVatCalcAmt, exchangeRate);
                yLatPerStart.setPreTotOutVatAmt(outVatFcyAmtForInt);*/
            }
        } catch (Exception e) {
            // 
        }
        return outputVatCalcAmt;
    }

    //
    private String performTotalVat(String preOutVatAmt, String outputVatCalcAmt) {
        String totalOutVat = "";
        try {
            BigDecimal yPreAccVatAmt = new BigDecimal(preOutVatAmt); // 3.40
            BigDecimal yCurrAccVatAmt = new BigDecimal(outputVatCalcAmt);// 6.79
            totalOutVat = yCurrAccVatAmt.subtract(yPreAccVatAmt).setScale(2, RoundingMode.HALF_UP).toString(); // 3.39
        } catch (Exception e) {
            //
        }
        return totalOutVat;
    }

    //
    private String updateOutVatAmt(StmtEntryRecord accountingEntry, PeriodStartClass yPeriodStartObj,
            String outputVatCalcAmt, String exchangeRate) {
        if (!accountingEntry.getAmountLcy().getValue().isBlank()
                && accountingEntry.getAmountFcy().getValue().isBlank()
                && !accountingEntry.getAmountLcy().getValue().startsWith("-")) {
            //Set the total Lcy amount when interest transaction type is set as income
            yPeriodStartObj.setTotOutVatLcyAmtForInt(outputVatCalcAmt);
            yPeriodStartObj.setPreTotOutVatAmt(outputVatCalcAmt);
        } else if (!accountingEntry.getAmountFcy().getValue().isBlank()
                && !accountingEntry.getAmountFcy().getValue().startsWith("-")) {
            //Set the total Fcy amount when interest transaction type is set as income
            yPeriodStartObj.setPreTotOutVatAmt(outputVatCalcAmt);
            yPeriodStartObj.setTotOutVatFcyAmtForInt(outputVatCalcAmt);
            yPeriodStartObj.setTotOutVatLcyAmtForInt(getExchangeCurrencyAmt(outputVatCalcAmt, exchangeRate));
            logger.debug("String yPeriodStartObj = "+ yPeriodStartObj);
            /**yPeriodStartObj.setTotOutVatFcyAmtForInt(getExchangeCurrencyAmt(outputVatCalcAmt, exchangeRate));
            yPeriodStartObj.setPreTotOutVatAmt(getExchangeCurrencyAmt(outputVatCalcAmt, exchangeRate));
            outputVatCalcAmt = getExchangeCurrencyAmt(outputVatCalcAmt, exchangeRate); */
        }
        return outputVatCalcAmt;
    }

    //
    public String updateChgDets(AccountRecord yAcctRec, String systemId, StmtEntryRecord accountingEntry,
            String outputVatCalcAmt, String exchangeRate, String taxRateVal, String revFlagInd) {
        String transConcatDets = "";
        String delimit = "###";
        String paymentType = "";
        String feeTransactionType = "";
        String chargeDetail = "";
        String yFeeAmt = "";
        String yFeeCcy = "";
        String dateOfFeeTransaction = accountingEntry.getBookingDate().getValue();
        String valueDateOfFeeTransaction = accountingEntry.getValueDate().getValue();
        String vatRateExchgeRate = "";
        String outVatRateForFee = "";
        String outVatFcyAmtForFee = "";
        String outVatLcyAmtForFee = "";

        String inpVatFcyAmountForFee = "";

        try {
            if (systemId.equals(arrIden)) {
                paymentType = systemId;
                Contract yContract = new Contract(this);
                yContract.setContractId(yAcctRec.getArrangementId().getValue());
                chargeDetail = updateChargeDets(accountingEntry);
                yFeeCcy = accountingEntry.getCurrency().getValue();
            }
            if (systemId.equals(paymentIden)) {
                logger.debug("*** Payment Condition Satisfied ***" +systemId);
                paymentType = systemId;
                String transRef = accountingEntry.getTransReference().getValue();
                PorTransactionRecord yPorTransRec = new PorTransactionRecord(da.getRecord(POR_TRANSACTION, transRef));
                if (yPorTransRec.getCreditmainaccount().getValue().startsWith(plCategEntMne)
                        || yPorTransRec.getDebitmainaccount().getValue().startsWith(plCategEntMne)) {
                    logger.debug("*** Pl Mnemonic Condition satisfied ***");
                    if (yPorTransRec.getSendersreferenceincoming().getValue().startsWith("PI")) {
                        PaymentOrderRecord yPORec = new PaymentOrderRecord(
                                da.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "PAYMENT.ORDER",
                                        "", yPorTransRec.getSendersreferenceincoming().getValue()));
                        chargeDetail = yPORec.getLocalRefField("CSD.FEE.TYPE").getValue();
                    } else {
                        logger.debug("*** OE Record Condition satisfied ***");
                        PpOrderEntryRecord orderEntRec = getOrderEntryRec(transRef);
                        chargeDetail = orderEntRec.getLocalRefField("CSD.FEE.TYPE").getValue();
                        logger.debug("String chargeDetail = "+chargeDetail);
                    }
                    yFeeCcy = accountingEntry.getCurrency().getValue();
                }
                if (!yPorTransRec.getCreditmainaccount().getValue().startsWith(plCategEntMne)
                        && !yPorTransRec.getDebitmainaccount().getValue().startsWith(plCategEntMne)) {
                    PpOrderEntryRecord orderEntRec = getOrderEntryRec(transRef);
                    String yPPChargeDets = "";
                    for (DebitchargecomponentClass debChrgComp : orderEntRec.getDebitchargecomponent()) {
                        String deptChrgAmt = debChrgComp.getDebitchargeamount().getValue();
                        /**String amtFcy = accountingEntry.getAmountFcy().getValue();*/
                        String debChgAmt = getCategAmt(accountingEntry);
                        try {
                            boolean isDebtAmt = new BigDecimal(deptChrgAmt).compareTo(new BigDecimal(debChgAmt)) == 0;
                            if (isDebtAmt) {
                                yPPChargeDets = debChrgComp.getDebitchargecomponent().getValue();
                                chargeDetail = yPPChargeDets;
                                break;
                            }
                        } catch (Exception e) {
                            // 
                        }
                    }
                    if (yPPChargeDets.isBlank()) {
                        for (CreditchargecomponentClass credChrgComp : orderEntRec.getCreditchargecomponent()) {
                            String credChrgAmt = credChrgComp.getCreditchargeamount().getValue();
                            /**String amtFcy = accountingEntry.getAmountFcy().getValue();*/
                            String CdtChgAmt = getCategAmt(accountingEntry);
                            try {
                                boolean isCdtrAmt = new BigDecimal(credChrgAmt).compareTo(new BigDecimal(CdtChgAmt)) == 0;
                                if (isCdtrAmt) {
                                    yPPChargeDets = credChrgComp.getCreditchargecomponent().getValue();
                                    chargeDetail = yPPChargeDets;
                                    break;
                                }
                            } catch (Exception e) {
                                // 
                            }
                        }
                    }
                    
                    PorPostingAndConfirmationRecord yPostAndConfRec = getPorPostAndConfRec(transRef);
                    String categAmtVal = getCategAmt(accountingEntry);
                    for (ChargePartyIndicatorClass chrgPtryInd : yPostAndConfRec.getChargePartyIndicator()) {
                        if (chrgPtryInd.getFeeType().getValue().equals(yPPChargeDets)) {
                            BigDecimal chrgAmtFeeCcyBd = new BigDecimal(chrgPtryInd.getChargeAmountFeeCurrency().getValue())
                                    .setScale(2, RoundingMode.HALF_UP);
                            if (accountingEntry.getAmountFcy().getValue().equals("") &&
                                    categAmtVal.equals(chrgAmtFeeCcyBd.toString())) {
                                /**yFeeAmt = chrgPtryInd.getChargeAmount().getValue();
                                yFeeCcy = chrgPtryInd.getChargeAmountCurrency().getValue();*/
                                yFeeAmt = chrgAmtFeeCcyBd.toString();
                                yFeeCcy = chrgPtryInd.getFeeCurrencyCode().getValue();
                            } else if (!accountingEntry.getAmountFcy().getValue().isBlank() &&
                                    categAmtVal.equals(chrgAmtFeeCcyBd.toString())) {
                                yFeeAmt = chrgAmtFeeCcyBd.toString();
                                yFeeCcy = chrgPtryInd.getFeeCurrencyCode().getValue();
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 
        }
        
        if (systemId.equals("CH") || systemId.equals(accIden)
                || systemId.equals(ftIden)) {
            paymentType = systemId;
            yFeeCcy = accountingEntry.getCurrency().getValue();
        }
        // For both Local and Foreign Currency Transaction, reference the AmountLcy for
        // FeeTransactionType
        if (!accountingEntry.getAmountLcy().getValue().isBlank() &&
                accountingEntry.getAmountFcy().getValue().isBlank() && !inputVatind) {
            logger.debug("*** String inputVatind condition satisfied ***" +inputVatind);
            yFeeAmt = accountingEntry.getAmountLcy().getValue();
            feeTransactionType = yIncome;
            outVatRateForFee = taxRateVal;
            outVatLcyAmtForFee = outputVatCalcAmt;
        } else if (!accountingEntry.getAmountFcy().getValue().isBlank()
                && !accountingEntry.getAmountFcy().getValue().isBlank() && !inputVatind) { // Set the Foreign Exchange rate
            logger.debug("*** String inputVatind condition satisfied ***" +inputVatind);
            yFeeAmt = accountingEntry.getAmountFcy().getValue();
            feeTransactionType = yIncome;
            outVatRateForFee = taxRateVal;
            vatRateExchgeRate = exchangeRate;
            outVatFcyAmtForFee = outputVatCalcAmt;
            outVatLcyAmtForFee = getExchangeCurrencyAmt(outVatFcyAmtForFee, vatRateExchgeRate);
        }

        if (inputVatind) {
            logger.debug("*** String inputVatind condition satisfied ***" +inputVatind);
            String inpVatRateForFee = taxRateVal;
            String inpVatLcyAmountForFee = outputVatCalcAmt;
            logger.debug("String inpVatRateForFee = "+inpVatRateForFee);
            logger.debug("String inpVatLcyAmountForFee = "+inpVatLcyAmountForFee);
            if (flagFcy) {
                vatRateExchgeRate = exchangeRate;
                inpVatFcyAmountForFee = outputVatCalcAmt;
                yFeeAmt = accountingEntry.getAmountFcy().getValue();
                inpVatLcyAmountForFee = getExchangeCurrencyAmt(inpVatFcyAmountForFee, vatRateExchgeRate);
                logger.debug("String vatRateExchgeRate = "+vatRateExchgeRate);
                logger.debug("String inpVatFcyAmountForFee = "+inpVatFcyAmountForFee);
                logger.debug("String yFeeAmt = "+yFeeAmt);
                logger.debug("String inpVatLcyAmountForFee = "+inpVatLcyAmountForFee);
            } else {
                yFeeAmt = accountingEntry.getAmountLcy().getValue();
                logger.debug("String yFeeAmt = "+yFeeAmt);
            }
            BigDecimal yFeeAmtval = new BigDecimal(yFeeAmt);
            transConcatDets = paymentType.concat(delimit).concat(yExpense).concat(delimit).concat(chargeDetail)
                    .concat(delimit).concat(yFeeAmtval.abs().toString()).concat(delimit).concat(yFeeCcy).concat(delimit)
                    .concat(dateOfFeeTransaction).concat(delimit).concat(valueDateOfFeeTransaction).concat(delimit)
                    .concat(vatRateExchgeRate).concat(delimit).concat(outVatRateForFee).concat(delimit)
                    .concat(outVatFcyAmtForFee).concat(delimit).concat(outVatLcyAmtForFee).concat(delimit)
                    .concat(inpVatRateForFee).concat(delimit).concat(inpVatLcyAmountForFee).concat(delimit)
                    .concat(inpVatFcyAmountForFee).concat(delimit).concat(vatCalcType).concat(delimit).concat(revFlagInd)
                    .concat(delimit).concat(ySession.getCurrentVariable("!TODAY")).concat(delimit)
                    .concat("R");

        } else {
            BigDecimal yOutFeeAmtval = new BigDecimal(yFeeAmt);
            transConcatDets = paymentType.concat(delimit).concat(feeTransactionType).concat(delimit)
                    .concat(chargeDetail).concat(delimit).concat(yOutFeeAmtval.abs().toString()).concat(delimit).concat(yFeeCcy)
                    .concat(delimit).concat(dateOfFeeTransaction).concat(delimit).concat(valueDateOfFeeTransaction)
                    .concat(delimit).concat(vatRateExchgeRate).concat(delimit).concat(outVatRateForFee).concat(delimit)
                    .concat(outVatFcyAmtForFee).concat(delimit).concat(outVatLcyAmtForFee).concat(delimit).concat("")
                    .concat(delimit).concat("").concat(delimit).concat("").concat(delimit).concat("").concat(delimit)
                    .concat(revFlagInd).concat(delimit).concat(ySession.getCurrentVariable("!TODAY")).concat(delimit)
                    .concat("R");
        }
        return transConcatDets;
    }

    //
    private PorPostingAndConfirmationRecord getPorPostAndConfRec(String transRef) {
        try {
            return new PorPostingAndConfirmationRecord(da.getRecord("POR.POSTING.AND.CONFIRMATION", transRef));
        } catch (Exception e) {
            return new PorPostingAndConfirmationRecord(this);
        }
    }

    //
    private String getCategAmt(StmtEntryRecord accountingEntry) {
        if (accountingEntry.getAmountFcy().getValue().isBlank()) {
            return accountingEntry.getAmountLcy().getValue();
        } else {
            return accountingEntry.getAmountFcy().getValue();
        }
    }

    //
    private String updateChargeDets(StmtEntryRecord accountingEntry) {
        String chrgeProperty = "";
        try {
            String transRef = accountingEntry.getTransReference().getValue();
            AaArrangementActivityRecord yArrActRec = new AaArrangementActivityRecord(da.getRecord(
                    ySession.getCompanyRecord().getFinancialMne().getValue(), "AA.ARRANGEMENT.ACTIVITY", "", transRef));
            chrgeProperty = yArrActRec.getActivity().getValue().split("-")[2];
        } catch (Exception e) {
            //
        }
        return chrgeProperty;
    }

    //
    private PpOrderEntryRecord getOrderEntryRec(String transRef) {
        PpOrderEntryRecord yOrderEntRec = new PpOrderEntryRecord(this);
        try {
            PorSupplementaryInfoRecord ySuppInfoRec = new PorSupplementaryInfoRecord(
                    da.getRecord("POR.SUPPLEMENTARY.INFO", transRef));
            if (ySuppInfoRec.getOrderEntryId().size() != 0) {
                String orderEntId = ySuppInfoRec.getOrderEntryId(ySuppInfoRec.getOrderEntryId().size() - 1).getValue();
                yOrderEntRec = new PpOrderEntryRecord(da.getRecord("PP.ORDER.ENTRY", orderEntId));
            }
        } catch (Exception e) {
            //
        }
        return yOrderEntRec;
    }

    //
    private AccountRecord getAcctDets(String acctNumber) {
        AccountRecord acctRecord = new AccountRecord(this);
        try {
            acctRecord = new AccountRecord(
                    da.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "ACCOUNT", "", acctNumber));
        } catch (Exception e) {
            //
        }
        return acctRecord;
    }

    //
    private EbCsdTaxDetailsUpdRecord checkLiveTable(String acctNumber) {
        EbCsdTaxDetailsUpdRecord yTaxDetsUpdRec = new EbCsdTaxDetailsUpdRecord(this);
        try {
            yTaxDetsUpdRec = new EbCsdTaxDetailsUpdRecord(
                    da.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.TAX.DETAILS.UPD", "",
                            acctNumber));
            yLiveRecFlag = true;
        } catch (Exception e) {
            //
        }
        return yTaxDetsUpdRec;
    }

    //
    private int checkBestFixIndex(String prodCateg, CustomerRecord yCustRec, String plCategParamId) {
        int vatIndex = 0;
        String matchCategInd = getBestFitIndex(yCustRec, prodCateg, plCategParamId);
        logger.debug("String matchCategInd = "+matchCategInd);
        if (!matchCategInd.equals("")) {
            vatIndex = Integer.parseInt(matchCategInd);
            logger.debug("String vatIndex = "+ vatIndex);
        }
        return vatIndex;
    }

    //
    private String getBestFitIndex(CustomerRecord yCustRec, String prodCateg, String plCategParamId) {
        logger.debug("*** Invoked getBestFitIndex method ***");
        String custSec = yCustRec.getSector().getValue();
        String regCountry = yCustRec.getRegCountry().getValue();
        String defFitIndex = "";
        String index = "";
        try {
            EbCsdVatWhtBestFitUpdRecord fitRec = new EbCsdVatWhtBestFitUpdRecord(
                    da.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(),
                            "EB.CSD.VAT.WHT.BEST.FIT.UPD", "", plCategParamId));
            String otherRegionCn = fitRec.getOtherRegionCn().getValue();
            String otherRegionCnPos = fitRec.getOtherRegionCnPos().getValue();
            String regionCn = fitRec.getRegionCn().getValue();
            String regionCnPos = fitRec.getRegionCnPos().getValue();
            logger.debug("String otherRegionCn = "+otherRegionCn);
            logger.debug("String otherRegionCnPos = "+otherRegionCnPos);
            logger.debug("String regionCn = "+regionCn);
            logger.debug("String regionCnPos = "+regionCnPos);
            
            String[] arrConfig = (!custSec.isEmpty() && (regCountry.isEmpty() || !"CN".equals(regCountry)))
                    ? otherRegionCn.split("#")
                    : regionCn.split("#");
            
            String[] arrPos = (!custSec.isEmpty() && (regCountry.isEmpty() || !"CN".equals(regCountry)))
                    ? otherRegionCnPos.split("#")
                    : regionCnPos.split("#");
            
            for (int i = 0; i < arrConfig.length; i++) {
                String cfg = arrConfig[i];
                String[] parts = cfg.split("\\*", -1);
                // get the Default index position, when client category, 
                // Client Location and Product Category is empty
                if (parts[0].isBlank() && parts[1].isBlank()
                        && parts[2].isBlank()) {
                    defFitIndex = arrPos[i];
                }
                // Matching sector
                if (parts[0].equals(custSec)) {
                    String configCountry = parts[1]; // may be "", CN, or other
                    String configProd = parts[2]; // may be "", prodCateg, or other
                    
                    boolean countryMatches = checkCountry(configCountry);
                    boolean productMatches = checkProduct(configProd, prodCateg);
                    
                    if (countryMatches && productMatches) {
                        index = arrPos[i];
                        yBestFitFlag = true;
                        break;
                    }
                }
            }
            if (!yBestFitFlag) {
                index = defFitIndex;
                yBestFitFlag = true;
            }
            logger.debug("String index = "+index);
            logger.debug("String yBestFitFlag = "+yBestFitFlag);
        } catch (Exception e) {
            //
        }
        return index;
    }

    /**
     * @param configProd
     * @param prodCateg
     * @return
     */
    private boolean checkProduct(String configProd, String prodCateg) {
        return configProd.isEmpty() || // blank allowed
                configProd.equals(prodCateg); // exact match
    }

    //
    private boolean checkCountry(String configCountry) {
        return configCountry.isEmpty() || // blank allowed
                "CN".equals(configCountry) || // CN allowed
                !configCountry.equals("CN"); // non-CN allowed (original logic) -->Means Any other cntry
    }

    
    //
    private String calculateTaxAmt(String amtLcy, String taxRate) {
        BigDecimal finalAmt = BigDecimal.ZERO;
        try {
            BigDecimal grossAmt = new BigDecimal(amtLcy); // Gross amount
            BigDecimal vatRatePercent = new BigDecimal(taxRate); // VAT rate in percent (e.g., 20%)
            // Convert VAT rate percent to decimal (e.g., 20% → 0.20)
            BigDecimal vatRate = vatRatePercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            // Formula: calculatedAmt = grossAmt / (1 + vatRate) * vatRate
            BigDecimal denominator = BigDecimal.ONE.add(vatRate);
            BigDecimal netAmt = grossAmt.divide(denominator, 10, RoundingMode.HALF_UP);
            BigDecimal calculatedAmt = netAmt.multiply(vatRate);
            // Round to 2 decimal places (for currency format)
            finalAmt = calculatedAmt.setScale(2, RoundingMode.HALF_UP).abs();
        } catch (Exception e) {
            //
        }
        return finalAmt.toString();
    }

    //
    private EbCsdVatWhtRateParamRecord getVatWhtParamRec(String plCategParamId) {
        EbCsdVatWhtRateParamRecord yVatWhtParamRec = new EbCsdVatWhtRateParamRecord(this);
        try {
            yVatWhtParamRec = new EbCsdVatWhtRateParamRecord(
                    da.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.VAT.WHT.RATE.PARAM",
                            "", plCategParamId));
        } catch (Exception e) {
            //
        }
        return yVatWhtParamRec;
    }

    //
    private String checkPLCategEntry(String plCategory) {
        boolean yFndFlag = false;
        String vatWhtRateParamId = "";
        logger.debug("String ySystemId = "+ySystemId);
        EbCsdStorePlCategParamRecord yPlCategParamRec = new EbCsdStorePlCategParamRecord(
                da.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.STORE.PL.CATEG.PARAM",
                        "", ySystemId));
        logger.debug("String yPlCategParamRec = "+ yPlCategParamRec);
        for (TField plCategVal : yPlCategParamRec.getPlCategParam()) {
            String rangeStart = "";
            String rangeEnd = "";
            try {
                if (plCategVal.getValue().contains("-")) {
                    rangeStart = plCategVal.getValue().split("-")[0];
                    rangeEnd = plCategVal.getValue().split("-")[1];
                    if (Integer.parseInt(plCategory) >= Integer.parseInt(rangeStart)
                            && Integer.parseInt(plCategory) <= Integer.parseInt(rangeEnd)) {
                        vatWhtRateParamId = plCategVal.getValue();
                        yFndFlag = true;
                    }
                } else if (!plCategVal.getValue().contains("-")
                        && (Integer.parseInt(plCategory) >= Integer.parseInt(plCategVal.getValue())
                                && Integer.parseInt(plCategory) <= Integer.parseInt(plCategVal.getValue()))) {
                    vatWhtRateParamId = plCategVal.getValue();
                    yFndFlag = true;
                }
            } catch (Exception e) {
                //
            }
            if (yFndFlag) {
                break;
            }
        }
        logger.debug("String yFndFlag = "+yFndFlag);
        logger.debug("String vatWhtRateParamId = "+vatWhtRateParamId);
        return vatWhtRateParamId;
    }
}