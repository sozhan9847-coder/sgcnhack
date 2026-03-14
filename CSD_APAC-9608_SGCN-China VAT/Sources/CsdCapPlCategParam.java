package com.temenos.csd.vat;

import java.util.ArrayList;
import java.util.List;

import com.temenos.api.TField;
import com.temenos.api.TStructure;
import com.temenos.t24.api.complex.eb.servicehook.TransactionData;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.ebcsdstoreplcategparam.EbCsdStorePlCategParamRecord;
import com.temenos.t24.api.records.ebcsdvatwhtrateparam.EbCsdVatWhtRateParamRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebcsdstoreplcategparam.EbCsdStorePlCategParamTable;

/**
 * @author v.manoj
 * EB.API>CSD.CAP.PL.CATEG.PARAM
 * Attached To: EB.TABLE.PROCEDURES>EB.CSD.VAT.WHT.RATE.PARAM
 * Attached As: AFTER.AUTH.PROC
 * Description: Routine to store the Parameterized PLCategory into EB.CSD.STORE.PL.CATEG.PARAM
 * 
 * @update v.manoj - TSR-1160810 - VAT Delivery: VAT Parameter configuration issue
 */
public class CsdCapPlCategParam extends RecordLifecycle {
    
    String ySysId = "SYSTEM";
    
    @Override
    public void postUpdateRequest(String application, String currentRecordId, TStructure currentRecord,
            List<TransactionData> transactionData, List<TStructure> currentRecords,
            TransactionContext transactionContext) {
        DataAccess da = new DataAccess(this);
        Session ySess = new Session(this);
        
        EbCsdVatWhtRateParamRecord VatWhtRateParamRec = new EbCsdVatWhtRateParamRecord(currentRecord);
        if (VatWhtRateParamRec.getRecordStatus().equalsIgnoreCase("REVE")) {
            // delete the parameterized PL Account from EB.CSD.STORE.PL.CATEG.PARAM table.
            clearPlParamEntry(currentRecordId, da, ySess);
        } else {
            EbCsdStorePlCategParamRecord yPlCategRec = new EbCsdStorePlCategParamRecord(this);
            try {
                yPlCategRec = new EbCsdStorePlCategParamRecord(da
                  .getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                          "EB.CSD.STORE.PL.CATEG.PARAM", "", ySysId));
                // Get the record from EB.CSD.STORE.PL.CATEG.PARAM
                List<String> plCategParamList = fetchPlCategParamList(yPlCategRec);
                if (!plCategParamList.contains(currentRecordId)) {
                    yPlCategRec.setPlCategParam(currentRecordId, yPlCategRec.getPlCategParam().size());
                    updateLiveTable(yPlCategRec);
                }
                
            } catch (Exception e) {
                yPlCategRec.setPlCategParam(currentRecordId, yPlCategRec.getPlCategParam().size());
                updateLiveTable(yPlCategRec);
            }
        }
        
    }

    //
    private void clearPlParamEntry(String currentRecordId, DataAccess da, Session ySess) {
        EbCsdStorePlCategParamRecord latPlCategRec = new EbCsdStorePlCategParamRecord(this);
        try {
            EbCsdStorePlCategParamRecord storePlCategRec = new EbCsdStorePlCategParamRecord(da
                    .getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                            "EB.CSD.STORE.PL.CATEG.PARAM", "", ySysId));
            int i = 0;
            for (TField tFplAcct : storePlCategRec.getPlCategParam()) {
                if (!tFplAcct.getValue().equals(currentRecordId)) {
                    latPlCategRec.setPlCategParam(tFplAcct.getValue(), i);
                    i++;
                }
            }
            updateLiveTable(latPlCategRec);
        } catch (Exception e) {
            //
        }
    }

    //
    private void updateLiveTable(EbCsdStorePlCategParamRecord yPlCategRec) {
        try {
            EbCsdStorePlCategParamTable yPlCategTab = new EbCsdStorePlCategParamTable(this);
            yPlCategTab.write(ySysId, yPlCategRec); 
        } catch (Exception e) {
            //
        }
    }

    //
    private List<String> fetchPlCategParamList(EbCsdStorePlCategParamRecord yPlCategRec) {
        List<String> addPlCategId = new ArrayList<>();
        for (TField plCategParamid : yPlCategRec.getPlCategParam()) {
            addPlCategId.add(plCategParamid.getValue());
        }
        return addPlCategId;
    }

}
