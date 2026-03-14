package com.temenos.csd.vat;

import java.util.List;

import com.temenos.api.TString;
import com.temenos.t24.api.complex.ac.clearingservicehook.ClearingContext;
import com.temenos.t24.api.complex.ac.clearingservicehook.Entry;
import com.temenos.t24.api.complex.ac.clearingservicehook.NameValuePair;
import com.temenos.t24.api.complex.ac.clearingservicehook.RequestDetail;
import com.temenos.t24.api.hook.accounting.ClearingService;
import com.temenos.t24.api.records.acentryparam.AcEntryParamRecord;
import com.temenos.t24.api.records.acentryparam.DataItemClass;
import com.temenos.t24.api.records.acentryparam.RecordTypeClass;
import com.temenos.t24.api.records.ebcsdacctentdetails.EbCsdAcctEntDetailsRecord;
import com.temenos.t24.api.records.ebcsdtaxdetsparam.EbCsdTaxDetsParamRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebcsdacctentdetails.EbCsdAcctEntDetailsTable;

/**
 * @author v.manoj EB.API>CSD.B.TRANS.ACCT.ENT Description: Routine to raise the
 *         accounting entries for the VAT/WHT
 */
public class CsdBTransAcctEnt extends ClearingService {

    Session ySess = new Session();

    @Override
    public void bookEntries(String id, ClearingContext clearingContext, String controlItem, TString processMode,
            RequestDetail requestDetail, List<Entry> entries) {

        ySess = new Session(this);
        DataAccess da = new DataAccess(this);
        // Get the Parameter record
        EbCsdTaxDetsParamRecord yDetsParamRec = getTaxDetsParamRecord(da);
        try {
            EbCsdAcctEntDetailsRecord yCsdAcctEntRec = new EbCsdAcctEntDetailsRecord(da.getRecord(
                    ySess.getCompanyRecord().getFinancialMne().getValue(), "EB.CSD.ACCT.ENT.DETAILS", "", id));
            // Get the debit and credit leg
            String acctEnt1Dets = yCsdAcctEntRec.getAccountingEntry().getValue();
            String transDets = chkRcvdLeg(acctEnt1Dets);
            // Get the Entry Param record
            AcEntryParamRecord yEntryParamRec = new AcEntryParamRecord(this);
            if (id.contains("$WHT")) {
                if(transDets.equalsIgnoreCase("Leg1")) {
                    yEntryParamRec = getAcEntryParamRecord(da, "CSD.WHT.ENTRY.PL");
                    requestDetail.setEntryParamId("CSD.WHT.ENTRY.PL");
                }else {
                    yEntryParamRec = getAcEntryParamRecord(da, yDetsParamRec.getWhtEntryParam().getValue());
                    requestDetail.setEntryParamId(yDetsParamRec.getWhtEntryParam().getValue());
                }
            } else {
                if (transDets.equalsIgnoreCase("Leg1")) {
                    yEntryParamRec =  getAcEntryParamRecord(da, "CSD.VAT.ENTRY.PL");
                    requestDetail.setEntryParamId("CSD.VAT.ENTRY.PL");
                } else if (transDets.equalsIgnoreCase("Leg2")) {
                    yEntryParamRec =  getAcEntryParamRecord(da, yDetsParamRec.getVatEntryParam().getValue());
                    requestDetail.setEntryParamId(yDetsParamRec.getVatEntryParam().getValue());
                }
            }

            String debitLeg = acctEnt1Dets.split("#")[0]; // Debit Leg
            Entry debLegAcctEntries = mapAcctEntryRecord(yEntryParamRec, debitLeg);
            // Add the entries to the outgoing list
            entries.add(debLegAcctEntries);
            String creditLeg = acctEnt1Dets.split("#")[1]; // Credit Leg
            Entry cedLegAcctEntries = mapAcctEntryRecord(yEntryParamRec, creditLeg);
            // Add the entries to the outgoing list
            entries.add(cedLegAcctEntries);
            processMode.set(debitLeg.split(",")[0]);
            // Setting the parameter values for OFS clearing request

            requestDetail.setCompanyId(ySess.getCompanyId());
            requestDetail.setOfsSourceId("CSD.TRANS.OFS");

            // After posting the OFS Message, delete the records
            // from EB.CSD.ACCT.ENT.DETAILS table for Interest entries
            if (!id.contains("*")) {
                deleteCreditEntries(id);
            }
        } catch (Exception e) {
            //
        }

    }

    //
    private String chkRcvdLeg(String acctEntDets) {
        String idenLeg = "";
        try {
            idenLeg = acctEntDets.split("\\*\\*")[1];
        } catch (Exception e) {
            //
        }
        return idenLeg;
    }

    //
    private Entry mapAcctEntryRecord(AcEntryParamRecord yEntryParamRec, String debitLeg) {
        Entry currentEntry = new Entry();
        for (RecordTypeClass recordType : yEntryParamRec.getRecordType()) {
            if (recordType.getRecordType().getValue().equals("DATA")) {
                currentEntry = formEntry(recordType.getDataItem(), debitLeg);
            }
        }
        return currentEntry;
    }

    //
    private Entry formEntry(List<DataItemClass> dataItemList, String acctEntDets) {
        Entry yCurrentEntry = new Entry();
        try {
            yCurrentEntry.setDataType("DATA");
            yCurrentEntry.setDataValue(acctEntDets.split(",")[1]);

            for (DataItemClass dataItem : dataItemList) {
                String dataItemKey = dataItem.getDataItem().getValue();
                NameValuePair nameVal = new NameValuePair();
                nameVal.setName(dataItemKey);
                switch (dataItemKey) {
                case "PL.CATEGORY":
                    nameVal.setValue(acctEntDets.split(",")[2]);
                    break;

                case "ACCOUNT":
                    nameVal.setValue(acctEntDets.split(",")[3]);
                    break;

                case "AMOUNT":
                    nameVal.setValue(acctEntDets.split(",")[4]);
                    break;

                case "CURRENCY":
                    nameVal.setValue(acctEntDets.split(",")[5]);
                    break;

                case "SIGN":
                    nameVal.setValue(acctEntDets.split(",")[6]);
                    break;

                case "TRANS.REFERENCE":
                    nameVal.setValue(acctEntDets.split(",")[7]);
                    break;

                case "CUSTOMER":
                    nameVal.setValue(acctEntDets.split(",")[8]);
                    break;

                case "ACCOUNT.OFFICER":
                    nameVal.setValue(acctEntDets.split(",")[9]);
                    break;

                case "PRODUCT.CATEGORY":
                    nameVal.setValue(acctEntDets.split(",")[10]);
                    break;

                case "OUR.REF":
                    nameVal.setValue(getOurRef(acctEntDets.split(",")[11]));
                    break;

                default:
                    nameVal.setValue("");
                    break;
                }
                yCurrentEntry.addValues(nameVal);
            }
        } catch (Exception ex) {
            //
        }
        return yCurrentEntry;
    }

    //
    private String getOurRef(String outReference) {
        if (outReference.contains("**")) {
            outReference = outReference.split("\\*\\*")[0];
        }
        return outReference;
    }
    
    //
    private void deleteCreditEntries(String id) {
        EbCsdAcctEntDetailsTable yAcctDetsTabRec = new EbCsdAcctEntDetailsTable(this);
        try {
            yAcctDetsTabRec.delete(id);
        } catch (Exception e) {
            //
        }
    }

    //
    private AcEntryParamRecord getAcEntryParamRecord(DataAccess da, String entryParamId) {
        try {
            return new AcEntryParamRecord(da.getRecord("AC.ENTRY.PARAM", entryParamId));
        } catch (Exception e) {
            return new AcEntryParamRecord();
        }
    }

    //
    private EbCsdTaxDetsParamRecord getTaxDetsParamRecord(DataAccess da) {
        try {
            return new EbCsdTaxDetsParamRecord(da.getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(), "",
                    "EB.CSD.TAX.DETS.PARAM", "SYSTEM"));

        } catch (Exception e) {
            return new EbCsdTaxDetsParamRecord();
        }
    }

}
