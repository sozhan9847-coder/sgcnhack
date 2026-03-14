package com.temenos.csd.vat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.records.acchargerequest.AcChargeRequestRecord;
import com.temenos.t24.api.records.account.AccountRecord;
import com.temenos.t24.api.records.ebcsdacctentdetails.EbCsdAcctEntDetailsRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.FeeTransactionReferenceClass;
import com.temenos.t24.api.records.ebcsdvatwhtrateparam.EbCsdVatWhtRateParamRecord;
import com.temenos.t24.api.records.fundstransfer.FundsTransferRecord;
import com.temenos.t24.api.records.stmtentry.StmtEntryRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebcsdacctentdetails.EbCsdAcctEntDetailsTable;
import com.temenos.t24.api.tables.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdTable;
import com.temenos.tafj.api.client.impl.T24Context;

/**
 * @author v.manoj 
 * AttachedTo : Call API 
 * AttachedAs : Call API 
 * Description: Routine to validate the Field content in Bulk file
 *              and move to out path when validation occur
 * * @update Jayaraman - TSR-1162200 - Sub division code logic is updated
 */


public class CsdCallRevProcessApi {
    
    String yClass = this.getClass().getSimpleName();
    T24Context ytext;
    
    boolean yLiveRecFlag = false;
    String revDbtTransCode = "0001";
    String revCdtTransCode = "0002";
    String yBulkNm = "CSMBULK";
    public static final String INPUT = "Input";
    public static final String OUTPUT = "Output";
    public static final String INCLUSIVE = "Inclusive";
    public static final String EXCLUSIVE = "Exclusive";
    
    private static Logger logger = LoggerFactory.getLogger("API");
    
    public CsdCallRevProcessApi(T24Context yText) {
        this.ytext = yText;
    }
    
    //
    public void raiseReversalEntry(StmtEntryRecord accountingEntry, String acctNumber, EbCsdVatWhtRateParamRecord yVatWhtParamRec, Session ySession, DataAccess da) {
        logger.debug(" ***** Reversal Call Api Invoked ***** ");
        acctNumber = getAcctNumber(acctNumber, accountingEntry, ySession, da);
        logger.debug("String acctNumber = "+acctNumber);
        try {
            // Read the Live Table
            EbCsdTaxDetailsUpdRecord ytaxDetsRec = new EbCsdTaxDetailsUpdRecord(da
                    .getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.TAX.DETAILS.UPD", "",
                                acctNumber));
            logger.debug("String ytaxDetsRec before set the reversal dets = "+ytaxDetsRec);
            for (FeeTransactionReferenceClass yFeeTransRef : ytaxDetsRec.getFeeTransactionReference()) {
                if (yFeeTransRef.getFeeTransactionReference().getValue()
                        .equals(accountingEntry.getTransReference().getValue())) {
                    // Update the reversal accounting entries details into EB.CSD.ACCT.ENT.DETAILS
                    updReversalAcctEnt(accountingEntry, acctNumber, yVatWhtParamRec, yFeeTransRef, ySession, da);
                    yFeeTransRef.setTransactionReversalDate(ySession.getCurrentVariable("!TODAY"));
                    yFeeTransRef.setTransactionReversalForFee(accountingEntry.getReversalMarker());
                    logger.debug("String ytaxDetsRec after set the reversal dets = "+ytaxDetsRec);
                    break;
                }
            }
            // Write the Transaction details into Live Table
            writeDetstoLiveTable(ytaxDetsRec, acctNumber);
        } catch (Exception e) {
            logger.debug("Exception thrown in the CsdCallRevProcessApi "+e.toString());
        }
        
    }

    //
    private String getAcctNumber(String acctNumber, StmtEntryRecord accountingEntry, Session ySession, DataAccess da) {
        String yAcctNo = "";
        if (acctNumber.startsWith("FT")) {
            yAcctNo = getFtAcctNum(acctNumber, accountingEntry, ySession, da);
        } else if (acctNumber.startsWith("CHG")) {
            yAcctNo = getChgeReqAcctNo(acctNumber, ySession, da);
        } else {
            yAcctNo = acctNumber; 
        }
        return yAcctNo;
    }

    //
    private String getChgeReqAcctNo(String acctNumber, Session ySession, DataAccess da) {
        String acAcctNum = "";
        try {
            AcChargeRequestRecord yAcChrgRequest = new AcChargeRequestRecord(da
                    .getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(),
                            "AC.CHARGE.REQUEST", "", acctNumber));
            acAcctNum = yAcChrgRequest.getDebitAccount().getValue();
        } catch (Exception e) {
            //
        }
        return acAcctNum;
    }

    //
    private String getFtAcctNum(String acctNumber, StmtEntryRecord accountingEntry, Session ySession, DataAccess da) {
        String yAccountNo = "";
        try {
            FundsTransferRecord yFtRecord = new FundsTransferRecord(da.getRecord(
                    ySession.getCompanyRecord().getFinancialMne().getValue(), "FUNDS.TRANSFER", "", acctNumber));
            // Take the credit Account for the Input VAT
            if (!accountingEntry.getAmountLcy().getValue().isBlank()
                    && accountingEntry.getAmountLcy().getValue().startsWith("-")) {
                yAccountNo = yFtRecord.getDebitAcctNo().getValue();
            }
            // Take the debit Account for the Output VAT
            if (!accountingEntry.getAmountLcy().getValue().isBlank()
                    && !accountingEntry.getAmountLcy().getValue().startsWith("-")) {
                yAccountNo = yFtRecord.getCreditAcctNo().getValue();
            }
        } catch (Exception e) {
            //
        }
        return yAccountNo;
    }

    //
    private void writeDetstoLiveTable(EbCsdTaxDetailsUpdRecord ytaxDetsRec, String acctNumber) {
        try {
            logger.debug(" ***** Reversal Live Table Write method Invoked ***** ");
            EbCsdTaxDetailsUpdTable yTaxDetsUpdtable = new EbCsdTaxDetailsUpdTable(ytext);
            yTaxDetsUpdtable.write(acctNumber, ytaxDetsRec);
            logger.debug("String yTaxDetsUpdtable = "+yTaxDetsUpdtable);
        } catch (Exception e) {
            //
        }
    }

    //
    private void updReversalAcctEnt(StmtEntryRecord accountingEntry, String acctNumber, EbCsdVatWhtRateParamRecord yVatWhtParamRec, FeeTransactionReferenceClass yFeeTransRef, Session ySession, DataAccess da) {
        // Set the Process mode based on lcy and fcy
        String yDelimit1 = ",";
        String processMode = "";
        String entriesAmt = "";
        String outputVat = yFeeTransRef.getOutputVatRateForFee().getValue();
        String inputVat = yFeeTransRef.getInputVatRateForFee().getValue();
        String inputVatCalcType = yFeeTransRef.getInputVatCalcTypeForFee().getValue();
        
        if (accountingEntry.getAmountFcy().getValue().isBlank() || !inputVat.isBlank()) {
            processMode = yBulkNm ;
        } else {
            processMode = "CSM";
        }
        
        entriesAmt = getOutputVatAmt(yFeeTransRef, accountingEntry, outputVat);
        entriesAmt = getInputVatAmt(yFeeTransRef, accountingEntry, entriesAmt, inputVat);       
        
        // Set the debit Leg for the AcctEntries1
        String revCdtPlCateg = "";
        String revCdtAcct = "";
        String revCdtAmt = "";
        String revCdtCcy = "";
        String revCdtTranSign = "C";
        String revCdtTransRef = "";
        String revCdtCustomer = "";
        String revCdtAcctOfficer = "";
        String revCdtProdCateg = "";
        String revCdtOurRef = "";
        
        if (!inputVat.isBlank() && outputVat.isBlank()) {
            revCdtAcct = getvatPayAcct(yVatWhtParamRec.getInputVatTempAccount().getValue(), accountingEntry, ySession, da);
        }  else if (!outputVat.isBlank() && inputVat.isBlank()) {
            revCdtPlCateg = accountingEntry.getPlCategory().getValue();
        }
        
        revCdtAmt = getCdtleg1Amt(accountingEntry, yFeeTransRef, entriesAmt);
        revCdtCcy = accountingEntry.getCurrency().getValue();
        revCdtTransRef = accountingEntry.getTransReference().getValue();
        revCdtCustomer = accountingEntry.getCustomerId().getValue();
        revCdtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        revCdtProdCateg = accountingEntry.getProductCategory().getValue();
        revCdtOurRef = accountingEntry.getOurReference().getValue();
        
        // Forming DebitLeg Message
        String revDbtLegEntries = processMode.concat(yDelimit1).concat(revCdtTransCode).concat(yDelimit1)
                .concat(revCdtPlCateg).concat(yDelimit1).concat(revCdtAcct).concat(yDelimit1).concat(revCdtAmt)
                .concat(yDelimit1).concat(revCdtCcy).concat(yDelimit1).concat(revCdtTranSign).concat(yDelimit1)
                .concat(revCdtTransRef).concat(yDelimit1).concat(revCdtCustomer).concat(yDelimit1)
                .concat(revCdtAcctOfficer).concat(yDelimit1).concat(revCdtProdCateg).concat(yDelimit1).concat(revCdtOurRef);
        
        // Set the CreditLeg for the AcctEntries1
        String revDbtPlCateg = "";
        String revDbtAcct = "";
        String revDbtAmt = "";
        String revDbtCcy = "";
        String revDbtTranSign = "D";
        String revDbtTransRef = "";
        String revDbtCustomer = "";
        String revDbtAcctOfficer = "";
        String revDbtProdCateg = "";
        String revDbtOurRef = "";
        
        if (!inputVat.isBlank()  && !inputVatCalcType.equals("") && inputVatCalcType.equalsIgnoreCase(INCLUSIVE)
                && accountingEntry.getAmountFcy().getValue().isBlank()) {
            revDbtPlCateg = accountingEntry.getPlCategory().getValue();
        } else if (inputVat.isBlank() && !outputVat.isBlank()
                || (!inputVat.isBlank() && !inputVatCalcType.equals("") && inputVatCalcType.equalsIgnoreCase(EXCLUSIVE))
                || checkIncFcy(inputVatCalcType, accountingEntry)) {
            revDbtAcct = getvatPayAcct(yVatWhtParamRec.getVatPayableAccount().getValue(), accountingEntry, ySession, da);
        }
        
        revDbtAmt = revCdtAmt;
        revDbtCcy = accountingEntry.getCurrency().getValue();
        revDbtTransRef = accountingEntry.getTransReference().getValue();
        revDbtCustomer = accountingEntry.getCustomerId().getValue();
        revDbtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        revDbtProdCateg = accountingEntry.getProductCategory().getValue();
        revDbtOurRef = accountingEntry.getOurReference().getValue();
        // Forming CreditLeg Message
        String revCbtLegEntries = processMode.concat(yDelimit1).concat(revDbtTransCode).concat(yDelimit1)
                .concat(revDbtPlCateg).concat(yDelimit1).concat(revDbtAcct).concat(yDelimit1).concat(revDbtAmt)
                .concat(yDelimit1).concat(revDbtCcy).concat(yDelimit1).concat(revDbtTranSign).concat(yDelimit1)
                .concat(revDbtTransRef).concat(yDelimit1).concat(revDbtCustomer).concat(yDelimit1)
                .concat(revDbtAcctOfficer).concat(yDelimit1).concat(revDbtProdCateg).concat(yDelimit1).concat(revDbtOurRef);

        EbCsdAcctEntDetailsRecord acctEntDetsRec = new EbCsdAcctEntDetailsRecord(ytext);
        acctEntDetsRec.setAccountingEntry(revDbtLegEntries.concat("#").concat(revCbtLegEntries).concat("**").concat("Leg1"));
        // Forming Unique Reference Number followed with account number
        String yUniqueId1 = UUID.randomUUID().toString();
        // Write the details into EB.CSD.ACCT.ENT.DETAILS
        writeAcctEntDets(yUniqueId1, acctEntDetsRec);
        
        if (!accountingEntry.getAmountFcy().getValue().isBlank()) {
            EbCsdAcctEntDetailsRecord acctEntDetsObj = new EbCsdAcctEntDetailsRecord(ytext);
            String acctEntriesLeg2 = formAcctEntLeg2(entriesAmt, revDbtAcct, accountingEntry, ySession, da);
            acctEntDetsObj.setAccountingEntry(acctEntriesLeg2);
            // Forming Unique Reference Number followed with account number
            writeAcctEntDets(UUID.randomUUID().toString(), acctEntDetsObj);
        }
        
    }

    //
    private String getInputVatAmt(FeeTransactionReferenceClass yFeeTransRef, StmtEntryRecord accountingEntry,
            String entriesAmt, String inputVat) {
        if (accountingEntry.getAmountFcy().getValue().isBlank() && entriesAmt.isBlank()
                && !inputVat.isBlank()) {
            entriesAmt = yFeeTransRef.getInputVatLcyAmountForFee().getValue();
        } else if (!accountingEntry.getAmountFcy().getValue().isBlank() && entriesAmt.isBlank()
                && !inputVat.isBlank()) {
            entriesAmt = yFeeTransRef.getInputVatFcyAmountForFee().getValue();
        }
        return entriesAmt;
    }

    //
    private String getOutputVatAmt(FeeTransactionReferenceClass yFeeTransRef, StmtEntryRecord accountingEntry, String outputVat) {
        String outVatAmt = "";
        if (accountingEntry.getAmountFcy().getValue().isBlank() && !outputVat.isBlank()) {
            outVatAmt = yFeeTransRef.getOutputVatLcyAmountForFee().getValue();
        } else if (!accountingEntry.getAmountFcy().getValue().isBlank() && !outputVat.isBlank()) {
            outVatAmt = yFeeTransRef.getOutputVatFcyAmountForFee().getValue();
        }
        return outVatAmt;
    }

    //
    private boolean checkIncFcy(String inputVatCalcType, StmtEntryRecord accountingEntry) {
        return (inputVatCalcType.equalsIgnoreCase(INCLUSIVE)
                && !accountingEntry.getAmountFcy().getValue().isBlank());
    }

    //
    private String getCdtleg1Amt(StmtEntryRecord accountingEntry, FeeTransactionReferenceClass yFeeTransRef, String entriesAmt) {
        String yEntAmount = "";
        if (accountingEntry.getAmountFcy().getValue().isBlank()) {
            yEntAmount = entriesAmt;
        } else {
            try {
                String vatExchgeRate = yFeeTransRef.getVatExchangeRateForFee().getValue();
                BigDecimal finalAmt = new BigDecimal(entriesAmt);
                BigDecimal outputVatExchangeRate = new BigDecimal(vatExchgeRate);
                yEntAmount = finalAmt.divide(outputVatExchangeRate, 10, RoundingMode.HALF_UP).
                        setScale(2, RoundingMode.HALF_UP).toString();
            } catch (Exception e) {
                //
            }
        }
        return yEntAmount;
    }

    //
    private String formAcctEntLeg2(String entriesAmt, String revCdtAcct, StmtEntryRecord accountingEntry, Session ySession, DataAccess da) {
        
        String yDelimit2 = ",";
        // Set the debitLeg for the AcctEntries2
        String yRevCdtPlCateg = "";
        String yRevCdtAcct = "";
        String yRevCdtAmt = "";
        String yRevCdtCcy = "";
        String yRevCdtTranSign = "C";
        String yRevCdtTransRef = "";
        String yRevCdtCust = "";
        String yRevCdtAcctOfficer = "";
        String yRevCdtProdCateg = "";
        String yRevCdtOurRef = "";
        
        yRevCdtAmt = entriesAmt;
        yRevCdtAcct = revCdtAcct;
        yRevCdtCcy = ySession.getLocalCurrency();
        yRevCdtTransRef = accountingEntry.getTransReference().getValue();
        yRevCdtCust = accountingEntry.getCustomerId().getValue();
        yRevCdtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        yRevCdtProdCateg = accountingEntry.getProductCategory().getValue();
        yRevCdtOurRef = accountingEntry.getOurReference().getValue();

        // Forming DebitLeg Message
        String yRevCreditLeg2Ent = yBulkNm.concat(yDelimit2).concat(revCdtTransCode).concat(yDelimit2).concat(yRevCdtPlCateg)
                .concat(yDelimit2).concat(yRevCdtAcct).concat(yDelimit2).concat(yRevCdtAmt).concat(yDelimit2)
                .concat(yRevCdtCcy).concat(yDelimit2).concat(yRevCdtTranSign).concat(yDelimit2)
                .concat(yRevCdtTransRef).concat(yDelimit2).concat(yRevCdtCust).concat(yDelimit2)
                .concat(yRevCdtAcctOfficer).concat(yDelimit2).concat(yRevCdtProdCateg).concat(yDelimit2).concat(yRevCdtOurRef);

        // Set the CreditLeg for the AcctEntries2
        String yRevDbtPlCateg = "";
        String yRevDbtAcct = "";
        String yRevDbtAmt = "";
        String yRevDbtCcy = "";
        String yRevDbtTranSign = "D";
        String yRevDbtTransRef = "";
        String yRevDbtCust = "";
        String yRevDbtAcctOfficer = "";
        String yRevDbtProdCateg = "";
        String yRevDbtOurRef = "";
        
        yRevDbtAcct = getLcyInternalAcct(yRevCdtAcct, ySession, da);
        yRevDbtAmt = yRevCdtAmt;
        yRevDbtCcy = ySession.getLocalCurrency();
        yRevDbtTransRef = accountingEntry.getTransReference().getValue();
        yRevDbtCust = accountingEntry.getCustomerId().getValue();
        yRevDbtAcctOfficer = accountingEntry.getAccountOfficer().getValue();
        yRevDbtProdCateg = accountingEntry.getProductCategory().getValue();
        yRevDbtOurRef = accountingEntry.getOurReference().getValue();
        // Forming CreditLeg Message
        String yRevDebitLeg2Ent = yBulkNm.concat(yDelimit2).concat(revDbtTransCode).concat(yDelimit2)
                .concat(yRevDbtPlCateg).concat(yDelimit2).concat(yRevDbtAcct).concat(yDelimit2).concat(yRevDbtAmt)
                .concat(yDelimit2).concat(yRevDbtCcy).concat(yDelimit2).concat(yRevDbtTranSign).concat(yDelimit2)
                .concat(yRevDbtTransRef).concat(yDelimit2).concat(yRevDbtCust).concat(yDelimit2)
                .concat(yRevDbtAcctOfficer).concat(yDelimit2).concat(yRevDbtProdCateg).concat(yDelimit2).concat(yRevDbtOurRef);

        return yRevDebitLeg2Ent.concat("#").concat(yRevCreditLeg2Ent).concat("**").concat("Leg2");
        
    }

    //
    private String getLcyInternalAcct(String yRevDbtAcct, Session ySession, DataAccess da) {
        String yIntLcyAcct = "";
        try {
            String yLocalCcy = ySession.getLocalCurrency(); // get the local currency
            String ySubDivCode = ySession.getCompanyRecord().getSubDivisionCode().getValue();
            String yCategId = yRevDbtAcct.substring(3, 8); // Get the Category
            String yLcyIntAcct = yLocalCcy.concat(yCategId).concat(ySubDivCode);
            List<String> categIntAcctList = da.getConcatValues("CATEG.INT.ACCT", yCategId);
            yIntLcyAcct = categIntAcctList.stream().filter(code -> code.startsWith(yLcyIntAcct)).findFirst()
                    .orElse(""); // Get the internal account for the respective currency
            if (yIntLcyAcct.isEmpty()) {
                logger.debug("*** getLcyInternalAcct method internalLcyAcct is empty ");
            String lcyIntCatAcctId = yLocalCcy.concat(yCategId);
            logger.debug("*** getLcyInternalAcct method internalLcyAcct is empty case lcyIntCatAcctId" +lcyIntCatAcctId);
            logger.debug("String lcyIntAcctId = "+ lcyIntCatAcctId);
            
            yIntLcyAcct = categIntAcctList.stream().filter(code -> code.startsWith(lcyIntCatAcctId)).findFirst()
                    .orElse(""); // Get the internal account for the respective currency
            logger.debug("*** getLcyInternalAcct method internalLcyAcct is empty case internalLcyAcct" +yIntLcyAcct);
            }
        } catch (Exception e) {
            //
        }
        return yIntLcyAcct;
    }

    //
    private String getvatPayAcct(String vatPayAcct, StmtEntryRecord accountingEntry, Session ySession, DataAccess da) {
        String yIntAcctNo = "";
        for (String intAcctNum : da.getConcatValues("CATEG.INT.ACCT", vatPayAcct)) {
            try {
                AccountRecord yIntAcctRec = new AccountRecord(da
                        .getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), "ACCOUNT", "", intAcctNum));
                if (yIntAcctRec.getCurrency().getValue().equals(accountingEntry.getCurrency().getValue())) {
                    yIntAcctNo = intAcctNum;
                    break;
                }
            } catch (Exception e) {
                //
            }
        }
        return yIntAcctNo;
    }

    //
    private void writeAcctEntDets(String yUniqueId, EbCsdAcctEntDetailsRecord acctEntDetsRec) {
        try {
            EbCsdAcctEntDetailsTable acctEntDetsTab = new EbCsdAcctEntDetailsTable(ytext);
            acctEntDetsTab.write(yUniqueId, acctEntDetsRec);
        } catch (Exception e) {
            //
        }
    }
    
}