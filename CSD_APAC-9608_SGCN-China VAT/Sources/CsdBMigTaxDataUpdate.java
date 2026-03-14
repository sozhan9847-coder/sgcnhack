package com.temenos.csd.vat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.complex.eb.servicehook.ServiceData;
import com.temenos.t24.api.hook.system.ServiceLifecycle;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.ClientBrdIdClass;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdRecord;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.FeeTransactionReferenceClass;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.InterestDetailClass;
import com.temenos.t24.api.records.ebcsdtaxdetailsupd.PeriodStartClass;
import com.temenos.t24.api.records.eblookup.EbLookupRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.tables.ebcsdtaxdetailsupd.EbCsdTaxDetailsUpdTable;

public class CsdBMigTaxDataUpdate extends ServiceLifecycle {
    
    private static final Logger logger = LoggerFactory.getLogger("API");
    
    @Override
    public void processSingleThreaded(ServiceData serviceData) {

        DataAccess da = new DataAccess(this);
        String yEbLookUpId = "CSD.TAX.FILE*PATH";
        String udPaths = da.getCurrentDirectory();
        String csvFilePath = "";
        EbLookupRecord yEbLookupyTaxDetailsUpdRec = new EbLookupRecord(this);

        try {
            yEbLookupyTaxDetailsUpdRec = new EbLookupRecord(da.getRecord("EB.LOOKUP", yEbLookUpId));
            String yEbFilePath = yEbLookupyTaxDetailsUpdRec.getDataName().get(0).getDataValue().getValue();
            if (yEbFilePath == null || yEbFilePath.trim().isEmpty()) {
                logger.error("EB.LOOKUP file path is empty for id " + yEbLookUpId);
                return;
            }
            // If path starts with '.', resolve using UD path
            if (yEbFilePath.startsWith(".")) {
                csvFilePath = udPaths + yEbFilePath.substring(1);
            } else {
                csvFilePath = yEbFilePath;
            }
            logger.debug("Resolved CSV file path: " + csvFilePath);
        } catch (Exception e) {
            logger.error("Could not read EB.LOOKUP yTaxDetailsUpdRecord with id " + yEbLookUpId, e);
            return;
        }

        EbCsdTaxDetailsUpdTable table = new EbCsdTaxDetailsUpdTable(this);
        try {
            List<String> lines = Files.readAllLines(Paths.get(csvFilePath));
            for (String line : lines) {
                if (line == null || line.trim().isEmpty() || line.startsWith("CustomerId")) {
                    continue;
                }
                String[] cols = line.split(",");
                if (cols.length < 8) {
                    logger.error("Invalid CSV line (less columns): " + line);
                    continue;
                }
                EbCsdTaxDetailsUpdRecord yTaxDetailsUpdRec = new EbCsdTaxDetailsUpdRecord();
                String custId = cols[0];
                for (String brdyTaxDetailsUpdRec : cols[1].split("##")) {
                    String[] f = brdyTaxDetailsUpdRec.split("\\$\\$");
                    ClientBrdIdClass brd = new ClientBrdIdClass();

                    setIfNotEmpty(brd::setClientBrdId, getSafe(f, 0));
                    setIfNotEmpty(brd::setClientCategory, getSafe(f, 1));
                    setIfNotEmpty(brd::setClientLocation, getSafe(f, 2));

                    yTaxDetailsUpdRec.addClientBrdId(brd);
                }
                setIfNotEmpty(yTaxDetailsUpdRec::setAccountNumber, cols[2]);
                setIfNotEmpty(yTaxDetailsUpdRec::setArrangementNumber, cols[3]);
                setIfNotEmpty(yTaxDetailsUpdRec::setAccountCategory, cols[4]);
                setIfNotEmpty(yTaxDetailsUpdRec::setAccountCurrency, cols[5]);

                populateFeeTransactionReferences(cols[6], yTaxDetailsUpdRec);
                populateInterestDetails(cols[7], yTaxDetailsUpdRec);

                table.write(custId, yTaxDetailsUpdRec);
            }
            // delete .csv file from the designated path
            deleteFile(csvFilePath);
            
        } catch (IOException e) {
            logger.error("Error reading CSV file", e);
        }
    }

    //
    private void deleteFile(String csvFilePath) {
        try {
            Files.delete(Paths.get(csvFilePath));
        } catch (Exception e) {
            // 
        }
    }

    private String getSafe(String[] arr, int idx) {
        return (arr != null && arr.length > idx) ? arr[idx] : null;
    }

    private void setIfNotEmpty(StringSetter setter, String value) {
        if (value != null && !value.trim().isEmpty()) {
            setter.set(value.trim());
        }
    }

    @FunctionalInterface
    interface StringSetter {
        void set(String value);
    }

    public void populateFeeTransactionReferences(String data, EbCsdTaxDetailsUpdRecord yTaxDetailsUpdRec) {

        if (data == null || data.isEmpty())
            return;

        for (String txn : data.split("##")) {

            String[] f = txn.split("\\$\\$");
            FeeTransactionReferenceClass yFee = new FeeTransactionReferenceClass();

            setIfNotEmpty(yFee::setFeeTransactionReference, getSafe(f, 0));
            setIfNotEmpty(yFee::setFeeTransactionType, getSafe(f, 1));
            setIfNotEmpty(yFee::setChargeDetail, getSafe(f, 2));
            setIfNotEmpty(yFee::setFeeAmount, getSafe(f, 3));
            setIfNotEmpty(yFee::setFeeCurrency, getSafe(f, 4));
            setIfNotEmpty(yFee::setDateOfFeeTransaction, getSafe(f, 5));
            setIfNotEmpty(yFee::setValueDateOfFeeTransaction, getSafe(f, 6));
            setIfNotEmpty(yFee::setVatExchangeRateForFee, getSafe(f, 7));
            setIfNotEmpty(yFee::setWhtExchangeRateForFee, getSafe(f, 8));
            setIfNotEmpty(yFee::setOutputVatRateForFee, getSafe(f, 9));
            setIfNotEmpty(yFee::setOutputVatFcyAmountForFee, getSafe(f, 10));
            setIfNotEmpty(yFee::setOutputVatLcyAmountForFee, getSafe(f, 11));
            setIfNotEmpty(yFee::setInputVatRateForFee, getSafe(f, 12));
            setIfNotEmpty(yFee::setInputVatCalcTypeForFee, getSafe(f, 13));
            setIfNotEmpty(yFee::setInputVatFcyAmountForFee, getSafe(f, 14));
            setIfNotEmpty(yFee::setInputVatLcyAmountForFee, getSafe(f, 15));
            setIfNotEmpty(yFee::setWhtRateForFee, getSafe(f, 16));
            setIfNotEmpty(yFee::setWhtCalculationTypeForFee, getSafe(f, 17));
            setIfNotEmpty(yFee::setWhtFcyAmountForFee, getSafe(f, 18));
            setIfNotEmpty(yFee::setWhtLcyAmountForFee, getSafe(f, 19));
            setIfNotEmpty(yFee::setTransactionReversalForFee, getSafe(f, 20));
            setIfNotEmpty(yFee::setTransactionReversalDate, getSafe(f, 21));

            yTaxDetailsUpdRec.addFeeTransactionReference(yFee);
        }
    }

    public void populateInterestDetails(String data, EbCsdTaxDetailsUpdRecord yTaxDetailsUpdRec) {

        if (data == null || data.isEmpty())
            return;

        for (String intyTaxDetailsUpdRec : data.split("##")) {
            String[] i = intyTaxDetailsUpdRec.split("\\$\\$");
            InterestDetailClass yInterest = new InterestDetailClass();

            setIfNotEmpty(yInterest::setInterestDetail, getSafe(i, 0));
            setIfNotEmpty(yInterest::setInterestTransactionType, getSafe(i, 1));
            setIfNotEmpty(yInterest::setInterestCurrency, getSafe(i, 2));
            setIfNotEmpty(yInterest::setVatExchangeRateForInterest, getSafe(i, 3));
            setIfNotEmpty(yInterest::setWhtExchangeRateForInterest, getSafe(i, 4));
            setIfNotEmpty(yInterest::setOutputVatRateForInterest, getSafe(i, 5));
            setIfNotEmpty(yInterest::setInputVatRateForInterest, getSafe(i, 6));
            setIfNotEmpty(yInterest::setWhtRateForInterest, getSafe(i, 7));

            String yPeriodData = getSafe(i, 8);
            if (yPeriodData != null) {
                for (String p : yPeriodData.split("\\^\\^")) {
                    String[] pf = p.split("%%");
                    PeriodStartClass yPeriod = new PeriodStartClass();

                    setIfNotEmpty(yPeriod::setPeriodStart, getSafe(pf, 0));
                    setIfNotEmpty(yPeriod::setPeriodEnd, getSafe(pf, 1));
                    setIfNotEmpty(yPeriod::setPreTotOutVatAmt, getSafe(pf, 2));
                    setIfNotEmpty(yPeriod::setTotalIntAmount, getSafe(pf, 3));
                    setIfNotEmpty(yPeriod::setTotOutVatFcyAmtForInt, getSafe(pf, 4));
                    setIfNotEmpty(yPeriod::setTotOutVatLcyAmtForInt, getSafe(pf, 5));
                    setIfNotEmpty(yPeriod::setTotInputVatFcyAmtForInt, getSafe(pf, 6));
                    setIfNotEmpty(yPeriod::setTotInputValLcyAmtForInt, getSafe(pf, 7));
                    setIfNotEmpty(yPeriod::setTotWhtFcyAmtForInt, getSafe(pf, 8));
                    setIfNotEmpty(yPeriod::setTotWhtLcyAmtForInt, getSafe(pf, 9));

                    yInterest.setPeriodStart(yPeriod, yInterest.getPeriodStart().size());
                }
            }
            yTaxDetailsUpdRec.addInterestDetail(yInterest);
        }
    }
}
