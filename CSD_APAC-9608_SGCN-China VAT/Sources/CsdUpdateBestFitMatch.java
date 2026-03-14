package com.temenos.csd.vat;

import java.util.List;
import com.temenos.api.TStructure;
import com.temenos.api.exceptions.T24IOException;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.ebcsdvatwhtbestfitupd.EbCsdVatWhtBestFitUpdRecord;
import com.temenos.t24.api.records.ebcsdvatwhtrateparam.ClientCategoryClass;
import com.temenos.t24.api.records.ebcsdvatwhtrateparam.EbCsdVatWhtRateParamRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;
import com.temenos.t24.api.tables.ebcsdvatwhtbestfitupd.EbCsdVatWhtBestFitUpdTable;

/**
 *
 * @author s.aananthalakshmi
 * @update v.manoj - TSR-1161516 - SocGen China VAT: Output VAT not working with default configuration
 *
 */
public class CsdUpdateBestFitMatch extends RecordLifecycle {

    @Override
    public void updateRecord(String application, String currentRecordId, TStructure currentRecord,
            TStructure unauthorisedRecord, TStructure liveRecord, TransactionContext transactionContext,
            List<com.temenos.t24.api.complex.eb.templatehook.TransactionData> transactionData,
            List<TStructure> currentRecords) {
        EbCsdVatWhtBestFitUpdRecord checkRcd = new EbCsdVatWhtBestFitUpdRecord(this);
        EbCsdVatWhtRateParamRecord paramRcd = new EbCsdVatWhtRateParamRecord(currentRecord);
        EbCsdVatWhtRateParamRecord paramOldrcd = new EbCsdVatWhtRateParamRecord(liveRecord);
        List<ClientCategoryClass> oldCategClass = paramOldrcd.getClientCategory();
        List<ClientCategoryClass> categClass = paramRcd.getClientCategory();
        int sizeOfOldCateg = oldCategClass.size();
        boolean allMatch = true;
        allMatch = chkOldCategSize(sizeOfOldCateg, currentRecordId);
        for (int catg = 0; catg < oldCategClass.size(); catg++) {
            ClientCategoryClass oldItem = oldCategClass.get(catg);
            ClientCategoryClass newItem = categClass.get(catg);

            if (!oldItem.getClientCategory().toString().equals(newItem.getClientCategory().toString())
                    || !oldItem.getClientLocation().toString().equals(newItem.getClientLocation().toString())
                    || !oldItem.getProductCategory().toString().equals(newItem.getProductCategory().toString())) {
                allMatch = false;
                break;
            }
        }

        if (allMatch) {
            return;
        }

        try {
            processClientCategories(categClass, checkRcd, currentRecordId);
        } catch (Exception e) {
            e.getLocalizedMessage();
        }

    }

    private void processClientCategories(List<ClientCategoryClass> categClass, EbCsdVatWhtBestFitUpdRecord checkRcd,
            String currentRecordId) throws T24IOException {
        String paramClientCateg = "";
        String paramClientLoc = "";
        String paramProductCategory = "";
        String combinedTarget = "";
        String delim = "*";
        int i = 0;
        EbCsdVatWhtBestFitUpdTable checkTab = new EbCsdVatWhtBestFitUpdTable(this);

        for (ClientCategoryClass categList : categClass) {
            paramClientCateg = categList.getClientCategory().getValue();
            paramClientLoc = categList.getClientLocation().getValue();
            paramProductCategory = categList.getProductCategory().getValue();
            combinedTarget = paramClientCateg.concat(delim).concat(paramClientLoc).concat(delim)
                    .concat(paramProductCategory);
            String combinedPos = Integer.toString(i);
            combinedTarget = combinedTarget.concat("#");
            combinedPos = combinedPos.concat("#");

            if (paramClientLoc.equals("") || (!paramClientLoc.equals("") && !paramClientLoc.equals("CN"))) {
                // Other than China Region
                checkRcd.setOtherRegionCn(checkRcd.getOtherRegionCn().getValue().concat(combinedTarget));
                checkRcd.setOtherRegionCnPos(checkRcd.getOtherRegionCnPos().getValue().concat(combinedPos));
                // Other than China and Acct products
            }
            if ((paramClientCateg.equals("") && paramClientLoc.equals("") && paramProductCategory.equals(""))
                    || (!paramClientLoc.equals("") && paramClientLoc.equals("CN"))) {
                // China Region
                checkRcd.setRegionCn(checkRcd.getRegionCn().getValue().concat(combinedTarget));
                checkRcd.setRegionCnPos(checkRcd.getRegionCnPos().getValue().concat(combinedPos));
            }
            i++;
        }
        try {
            if (categClass.size() > 0) {
                checkTab.write(currentRecordId, checkRcd);
            }
        } catch (T24IOException e) {
            //
        }

    }

    //
    private boolean chkOldCategSize(int sizeOfOldCateg, String currentRecordId) {
        boolean yallMatch = true;

        if (!recordExists("EB.CSD.VAT.WHT.BEST.FIT.UPD", currentRecordId))
            yallMatch = false;
        if (!recordExists("EB.CSD.VAT.WHT.RATE.PARAM", currentRecordId))
            yallMatch = false;
        if (sizeOfOldCateg == 0) {
            yallMatch = false; // rcd available and no categ configured
        }

        return yallMatch;

    }

    private boolean recordExists(String appName, String recordId) {
        DataAccess da = new DataAccess(this);
        Session ySession = new Session(this);
        try {
            da.getRecord(ySession.getCompanyRecord().getFinancialMne().getValue(), appName, "", recordId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}