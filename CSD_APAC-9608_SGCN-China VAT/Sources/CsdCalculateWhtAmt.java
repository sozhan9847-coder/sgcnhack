package com.temenos.csd.vat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.temenos.api.TField;
import com.temenos.api.TNumber;
import com.temenos.api.TStructure;
import com.temenos.csd.vat.CsdWithStandTaxCalc.VatWhtResult;
import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;
import com.temenos.t24.api.arrangement.accounting.Contract;
import com.temenos.t24.api.complex.st.calculationhook.ChargeAmount;
import com.temenos.t24.api.complex.st.calculationhook.TaxContext;
import com.temenos.t24.api.hook.contract.Calculation;
import com.temenos.t24.api.records.aaarrangement.AaArrangementRecord;
import com.temenos.t24.api.records.aaprddesaccounting.AaPrdDesAccountingRecord;
import com.temenos.t24.api.records.aaprddesaccounting.PropertyClass;
import com.temenos.t24.api.records.aaprddesinterest.AaPrdDesInterestRecord;
import com.temenos.t24.api.records.account.AccountRecord;
import com.temenos.t24.api.records.customer.CustomerRecord;
import com.temenos.t24.api.records.ebcsdstoreplcategparam.EbCsdStorePlCategParamRecord;
import com.temenos.t24.api.records.ebcsdvatwhtbestfitupd.EbCsdVatWhtBestFitUpdRecord;
import com.temenos.t24.api.records.ebcsdvatwhtrateparam.EbCsdVatWhtRateParamRecord;
import com.temenos.t24.api.records.tax.TaxRecord;
import com.temenos.t24.api.records.taxtypecondition.TaxTypeConditionRecord;
import com.temenos.t24.api.system.DataAccess;
import com.temenos.t24.api.system.Session;

/**
 * @author v.manoj
 * EB.API>CSD.CALCULATE.WHT.AMT
 * Attached To: TAX>VAT6
 * Attached As: CALC.ROUTINE
 * Description: Routine to calculate the WHT Amount based on the Parameterization
 *              in the EB.CSD.VAT.WHT.RATE.PARAM Table.
 * 
 * @update v.manoj - TSR-1151911 - China VAT Delivery: Issue with LCY Amount
 */
public class CsdCalculateWhtAmt extends Calculation {
    
    DataAccess yDataAccess = new DataAccess();
    Contract yContract = new Contract();
    Session ySess = new Session();
    String yInc = "Inclusive";
    String yExc = "Exclusive";
    String yExpense = "Expense";
    boolean yBstFitFlag = false;
    String yArrId = "";
    
    private static Logger logger = LoggerFactory.getLogger("API");
    
    @Override
    public ChargeAmount getTaxAmount(String customerId, TNumber dealAmount, String dealCurrency, String currencyMarket,
            TNumber crossRate, String crossCurrency, String drawDownCurrency, String percentageLevied, String taxId,
            TaxRecord taxRecord, TNumber taxAmount, String transactionId, TStructure transactionRecord,
            TaxContext taxContext) {

        logger.debug(" ***** Tax Api Routine called ***** ");
        logger.debug("String transactionId = "+ transactionId);
        logger.debug("String dealAmount = "+ dealAmount);
        logger.debug("String taxId = "+ taxId);
        logger.debug("String taxAmount = "+ taxAmount);
        logger.debug("String customerId = "+ customerId);
        
        ChargeAmount yChargeAmt = new ChargeAmount();
        ySess = new Session(this);
        yDataAccess = new DataAccess(this);
        yContract = new Contract(this);
        // get the arrangement Id
        yArrId = transactionId.split("\\-")[0];
        logger.debug("String yArrId = "+ yArrId);
        yContract.setContractId(yArrId);
        
        List<String> intPropertyList = yContract.getPropertyIdsForPropertyClass("INTEREST");
        logger.debug("String intPropertyList = "+ intPropertyList);
        String intProp = intPropertyList.get(0);
        logger.debug("String intProp = "+ intProp);
        AaPrdDesInterestRecord yPrdIntRec = new AaPrdDesInterestRecord(
                yContract.getConditionForProperty(intProp));
        logger.debug("String yPrdIntRec = "+ yPrdIntRec);
        String intProperty = yPrdIntRec.getIdComp2().getValue();  // Get the PropertyName from Interest Property
        logger.debug("String intProperty = "+ intProperty);
        // get the PLCategory from the
        String plCategory = getPlCategId(intProperty);
        logger.debug("String plCategory = "+ plCategory);
        // Check the PL Category is parameterized or not.
        String plCategParamId = checkPLCategParam(plCategory);
        logger.debug("String plCategParamId = "+ plCategParamId);
        if (!plCategParamId.isBlank()) {
            // Get the Account Details
            String acctNumber = getAccountDets(yArrId);
            logger.debug("String acctNumber = "+ acctNumber);
            AccountRecord yAcctRec = getAcctRecord(acctNumber);
            String prodCateg = yAcctRec.getCategory().getValue(); // I took Category from account for check the bestFit
            logger.debug("String prodCateg = "+ prodCateg);
            // Get the customer details
            CustomerRecord yCustomerRec = getCustomerDetails(customerId);
            // Get the Parameterized record for the respective PL Category
            EbCsdVatWhtRateParamRecord yVatWhtParamRec = getVatWhtParamRecord(plCategParamId);
            int bestFitPos = checkBestFixPos(prodCateg, yCustomerRec, plCategParamId);
            logger.debug("String bestFitPos = "+ bestFitPos);
            if (yBstFitFlag) {
                String yTaxType = yVatWhtParamRec.getClientCategory(bestFitPos).getTaxType().getValue();
                logger.debug("String yTaxType = "+ yTaxType);
                String yPLType = yVatWhtParamRec.getClientCategory(bestFitPos).getPlType().getValue();
                logger.debug("String yPLType = "+ yPLType);
                if (yTaxType.equalsIgnoreCase("Input") && yPLType.equalsIgnoreCase("Interest")) {
                    logger.debug(" ** Condition Satisfied ** ");
                    String yOutputWhtCalcAmt = "";
                    String whtCalcType = yVatWhtParamRec.getClientCategory(bestFitPos).getWhtCalculationType().getValue();
                    String whtRateCond = yVatWhtParamRec.getClientCategory(bestFitPos).getWhtRate().getValue();
                    String ytaxcde = fetchTaxCodeRec(whtRateCond);
                    String whtTaxRate = fetchTaxRate(ytaxcde);
                    logger.debug("String whtCalcType = "+ whtCalcType);
                    logger.debug("String whtRateCond = "+ whtRateCond);
                    logger.debug("String ytaxcde = "+ ytaxcde);
                    logger.debug("String whtTaxRate = "+ whtTaxRate);
                    // Get the WHT Calculated amount for the Inclusive WHT Type
                    yOutputWhtCalcAmt = getWhtIncAmount(whtCalcType, dealAmount, whtTaxRate, yVatWhtParamRec, bestFitPos, yOutputWhtCalcAmt);
                    // Get the WHT Calculated amount for the Exclusive WHT Type
                    yOutputWhtCalcAmt = getWhtExcAmount(whtCalcType, dealAmount, whtTaxRate, yVatWhtParamRec, bestFitPos, yOutputWhtCalcAmt);
                    logger.debug("String yOutputWhtCalcAmt = "+ yOutputWhtCalcAmt);
                    
                    if (!yOutputWhtCalcAmt.isBlank() && !yOutputWhtCalcAmt.equals("0")) {
                        yChargeAmt.setAmount(new TNumber(yOutputWhtCalcAmt)); // Set the Amount
                        logger.debug("String yChargeAmt = "+ yChargeAmt);
                    }
                }
            }
        }
        return yChargeAmt;
    }

    //
    private String fetchTaxRate(String ytaxcde) {
        String ytaxRate = "";
        try {
            List<String> taxCodeList = yDataAccess.selectRecords("", "TAX", "", "WITH @ID LIKE " + ytaxcde + "....");
            TaxRecord ytaxRec = new TaxRecord(yDataAccess.getRecord("TAX", getLatestTaxId(taxCodeList)));
            ytaxRate = ytaxRec.getRate().getValue();
        } catch (Exception e) {
            //
        }
        return ytaxRate;
    }

    //
    private String getLatestTaxId(List<String> taxCodeList) {
        String ylatestTaxId = "";
        if (taxCodeList.size() == 1) {
            ylatestTaxId = taxCodeList.get(0);
        }
        // if the id list is GT 1, split the date component from the id
        if (taxCodeList.size() > 1) {
            List<Integer> ydatesList = new ArrayList<>();
            String[] ytaxCodeDets = {};
            for (String tempCode : taxCodeList) {
                ytaxCodeDets = tempCode.split("\\.");
                ydatesList.add(Integer.parseInt(ytaxCodeDets[1]));
            }
            if (!ydatesList.isEmpty()) {
                ylatestTaxId = ytaxCodeDets[0] + "." + Collections.max(ydatesList).toString();
            }
        }
        return ylatestTaxId;
    }

    //
    private String fetchTaxCodeRec(String whtRateCond) {
        String ytaxcode = "";
        try {
            TaxTypeConditionRecord yTaxTypeCondRec = new TaxTypeConditionRecord(
                    yDataAccess.getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                        "TAX.TYPE.CONDITION", "", whtRateCond));
            int custTaxGrpLen = yTaxTypeCondRec.getCustTaxGrp().size();
            if (yTaxTypeCondRec.getCustTaxGrp().size() > 0) {
                int ycontractGrpLen = yTaxTypeCondRec.getCustTaxGrp(custTaxGrpLen-1).getContractGrp().size();
                if (ycontractGrpLen > 0) {
                    ytaxcode = yTaxTypeCondRec.getCustTaxGrp(custTaxGrpLen-1).
                            getContractGrp(ycontractGrpLen-1).getTaxCode().getValue();
                }
            }
        } catch (Exception e) {
            //
        }
        return ytaxcode;
    }

    //
    private String getWhtExcAmount(String whtCalcType, TNumber dealAmount, String whtTaxRate,
            EbCsdVatWhtRateParamRecord yVatWhtParamRec, int bestFitPos, String yOutputWhtCalcAmt) {
        if (!whtCalcType.equals("") && whtCalcType.equalsIgnoreCase(yExc) && yOutputWhtCalcAmt.isBlank()) {
            logger.debug(" *** WHT Exclusive Condition Satisfied *** ");
            String yInpVatCalType = yVatWhtParamRec.getClientCategory(bestFitPos).getInputVatCalculationType().getValue();
            String vatRateCond = yVatWhtParamRec.getClientCategory(bestFitPos).getVatRate().getValue();
            String vatTaxcde = fetchTaxCodeRec(vatRateCond);
            String vatTaxRate = fetchTaxRate(vatTaxcde);
            logger.debug("String yInpVatCalType = "+yInpVatCalType);
            logger.debug("String vatRateCond = "+vatRateCond);
            logger.debug("String vatTaxcde = "+vatTaxcde);
            logger.debug("String vatTaxRate = "+vatTaxRate);
            if (!yInpVatCalType.isBlank() && yInpVatCalType.equalsIgnoreCase(yInc)) {
                logger.debug(" *** Input VAT Inclusive Condition Satisfied *** ");
                VatWhtResult yresult = CsdWithStandTaxCalc.calculateVatWhtIncExc(new BigDecimal(dealAmount.get()), 
                        new BigDecimal(vatTaxRate), new BigDecimal(whtTaxRate));
                BigDecimal ywhtAmt = yresult.whtAmount;
                yOutputWhtCalcAmt = ywhtAmt.toString();
            } else if (!yInpVatCalType.isBlank() && yInpVatCalType.equalsIgnoreCase(yExc)) {
                logger.debug(" *** Input VAT Exclusive Condition Satisfied *** ");
                yOutputWhtCalcAmt = CsdWithStandTaxCalc.calculateWhExc(new BigDecimal(dealAmount.get()), new BigDecimal(whtTaxRate));
            } else if (yInpVatCalType.isBlank()) {
                logger.debug(" *** WHT alone Condition Satisfied *** ");
                yOutputWhtCalcAmt = CsdWithStandTaxCalc.calculateWhExc(new BigDecimal(dealAmount.get()), new BigDecimal(whtTaxRate));
            }
        }
        return yOutputWhtCalcAmt;
    }

    //
    private String getWhtIncAmount(String whtCalcType, TNumber dealAmount, String whtTaxRate, EbCsdVatWhtRateParamRecord yVatWhtParamRec, int bestFitPos, String yOutputWhtCalcAmt) {
        if (!whtCalcType.equals("") && whtCalcType.equalsIgnoreCase(yInc)) {
            logger.debug(" *** WHT Inclusive Condition Satisfied *** ");
            String inpVatCalType = yVatWhtParamRec.getClientCategory(bestFitPos).getInputVatCalculationType().getValue();
            logger.debug("String input inpVatCalType = "+ inpVatCalType);
            String vatRateCondition = yVatWhtParamRec.getClientCategory(bestFitPos).getVatRate().getValue();
            logger.debug("String input vatRateCondition = "+ vatRateCondition);
            String vatTaxcode = fetchTaxCodeRec(vatRateCondition);
            logger.debug("String input vatTaxcode = "+ vatTaxcode);
            String vatTaxRte = fetchTaxRate(vatTaxcode);
            logger.debug("String input vatTaxRte = "+ vatTaxRte);
            if (!inpVatCalType.isBlank() && inpVatCalType.equalsIgnoreCase(yInc)) {
                logger.debug(" *** Input VAT Inclusive Condition Satisfied *** ");
                VatWhtResult result = CsdWithStandTaxCalc.calculateVatWhtInclusive(new BigDecimal(dealAmount.get()),
                        new BigDecimal(vatTaxRte), new BigDecimal(whtTaxRate));
                logger.debug("String result = "+ result);
                BigDecimal whtAmt = result.whtAmount;
                yOutputWhtCalcAmt = whtAmt.toString();
                logger.debug("String yOutputWhtCalcAmt = "+ yOutputWhtCalcAmt);
            } else if (!inpVatCalType.isBlank() && inpVatCalType.equalsIgnoreCase(yExc)) {
                logger.debug(" *** Input VAT Exclusive Condition Satisfied *** ");
                yOutputWhtCalcAmt = CsdWithStandTaxCalc.calculateWhInc(new BigDecimal(dealAmount.get()), new BigDecimal(whtTaxRate));
            } else if (inpVatCalType.isBlank()) {
                logger.debug(" *** WHT alone Condition Satisfied *** ");
                yOutputWhtCalcAmt = CsdWithStandTaxCalc.calculateWhInc(new BigDecimal(dealAmount.get()), new BigDecimal(whtTaxRate));
            }
        }
        return yOutputWhtCalcAmt;
    }

    //
    private String getPlCategId(String intProperty) {
        String plCategId = "";
        try {
            // get the propertyId for the Accounting property class
            List<String> accountingPropList = yContract.getPropertyIdsForPropertyClass("ACCOUNTING");
            TStructure tAcctStruct = yContract.getConditionForProperty(accountingPropList.get(0));
            // get the pl category from the AA.PRD.DES.ACCOUNTING fot the Respective Tax Property
            AaPrdDesAccountingRecord accountingRec = new AaPrdDesAccountingRecord(tAcctStruct);
            logger.debug("String accountingRec = "+ accountingRec);
            for (PropertyClass yProperty : accountingRec.getProperty()) {
                if (yProperty.getProperty().getValue().equals(intProperty)) {
                    plCategId = yProperty.getBookingCm().getValue();
                    break;
                }
            }
        } catch (Exception e) {
            //
        }
        return plCategId;
    }

    //
    private int checkBestFixPos(String prodCateg, CustomerRecord yCustomerRec, String plCategParamId) {
        int vatPos = 0;
        String matchCategInd = getBestFitPosition(yCustomerRec, prodCateg, plCategParamId);
        if(!matchCategInd.equals("")) {
            vatPos = Integer.parseInt(matchCategInd);
        }
        return vatPos;
    }
    
    //
    private String getBestFitPosition(CustomerRecord yCustomerRec, String prodCateg, String plCategParamId) {
        String custSec = yCustomerRec.getSector().getValue();
        String regCountry = yCustomerRec.getRegCountry().getValue();
        String index = "";
        String defFitIndex = "";
        try {
            EbCsdVatWhtBestFitUpdRecord bestfitRec = new EbCsdVatWhtBestFitUpdRecord(
                    yDataAccess.getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                            "EB.CSD.VAT.WHT.BEST.FIT.UPD", "", plCategParamId));
            
            String otherRegionCn = bestfitRec.getOtherRegionCn().getValue();
            String otherRegionCnPos = bestfitRec.getOtherRegionCnPos().getValue();
            String regionCn = bestfitRec.getRegionCn().getValue();
            String regionCnPos = bestfitRec.getRegionCnPos().getValue();
            
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
                if (!parts[0].equals(custSec)) continue;

                String configCountry = parts[1];  // may be "", CN, or other
                String configProd    = parts[2];  // may be "", prodCateg, or other

                boolean countryMatches =
                        configCountry.isEmpty() ||      // blank allowed
                        "CN".equals(configCountry) ||   // CN allowed
                        !configCountry.equals("CN");     // non-CN allowed (original logic) -->Means Any other cntry

                boolean productMatches =
                        configProd.isEmpty() ||         // blank allowed
                        configProd.equals(prodCateg);    // exact match

                if (countryMatches && productMatches) {
                    index = arrPos[i];
                    yBstFitFlag = true;
                    break;
                }
            }
            if (!yBstFitFlag) {
                index = defFitIndex;
                yBstFitFlag = true;
            }
        } catch (Exception e) {
            //
        }
        return index;
    }

    //
    private EbCsdVatWhtRateParamRecord getVatWhtParamRecord(String plCategParamId) {
        EbCsdVatWhtRateParamRecord vatWhtParamRecObj = new EbCsdVatWhtRateParamRecord(this);
        try {
            vatWhtParamRecObj = new EbCsdVatWhtRateParamRecord(yDataAccess.
               getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                       "EB.CSD.VAT.WHT.RATE.PARAM", "", plCategParamId));
        } catch (Exception e) {
            //
        }
        return vatWhtParamRecObj;
    }

    //
    private AccountRecord getAcctRecord(String acctNumber) {
        AccountRecord acctRecObj = new AccountRecord(this);
        try {
            acctRecObj = new AccountRecord(
                    yDataAccess.getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                            "ACCOUNT", "", acctNumber));
        } catch (Exception e) {
            //
        }
        return acctRecObj;
    }

    //
    private String getAccountDets(String yArrId) {
        String yAcctNo = "";
        try {
            AaArrangementRecord yArrRec = new AaArrangementRecord(
                    yDataAccess.getRecord("AA.ARRANGEMENT", yArrId));
            yAcctNo = yArrRec.getLinkedAppl(0).getLinkedApplId().getValue();
        } catch (Exception e) {
            //
        }
        return yAcctNo;
    }

    //
    private String checkPLCategParam(String plCategory) {
        boolean paramIdFlg = false;
        String vatWhtParamIdVal = "";
        EbCsdStorePlCategParamRecord plCategParamRecord = new EbCsdStorePlCategParamRecord(
                yDataAccess.getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(),
                                        "EB.CSD.STORE.PL.CATEG.PARAM", "", "SYSTEM"));
        for (TField plCategVal : plCategParamRecord.getPlCategParam()) {
            String rangeStart = "";
            String rangeEnd = "";
            try {
                if (plCategVal.getValue().contains("-")) {
                    rangeStart = plCategVal.getValue().split("-")[0];
                    rangeEnd = plCategVal.getValue().split("-")[1];
                    if (Integer.parseInt(plCategory) >= Integer.parseInt(rangeStart)
                            && Integer.parseInt(plCategory) <= Integer.parseInt(rangeEnd)) {
                        vatWhtParamIdVal = plCategVal.getValue();
                        paramIdFlg = true;
                    }
                } else if (!plCategVal.getValue().contains("-") &&
                        (Integer.parseInt(plCategory) >= Integer.parseInt(plCategVal.getValue())
                        && Integer.parseInt(plCategory) <= Integer.parseInt(plCategVal.getValue()))) {
                    vatWhtParamIdVal = plCategVal.getValue();
                    paramIdFlg = true;
                }
            } catch (Exception e) {
                //
            }
            if (paramIdFlg) {
                break;
            }
        }
        return vatWhtParamIdVal;
    }

    //
    private CustomerRecord getCustomerDetails(String customerId) {
        CustomerRecord yCustRecord = new CustomerRecord(this);
        try {
            yCustRecord = new CustomerRecord(yDataAccess.
               getRecord(ySess.getCompanyRecord().getFinancialMne().getValue(), "CUSTOMER", "", customerId));
        } catch (Exception e) {
            //
        }
        return yCustRecord;
    }

    

}
