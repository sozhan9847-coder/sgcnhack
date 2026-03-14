package com.temenos.csd.vat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.temenos.logging.facade.Logger;
import com.temenos.logging.facade.LoggerFactory;

/**
 * VAT & WHT Calculation Author: s.aananthalakshmi
 */

public class CsdWithStandTaxCalc {
    
    private CsdWithStandTaxCalc() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    private static final RoundingMode R = RoundingMode.HALF_UP;
    static BigDecimal hundred = new BigDecimal("100");
    
    private static Logger logger = LoggerFactory.getLogger("API");
    
    public static class VatWhtResult {
         BigDecimal vatAmount = BigDecimal.ZERO;
         BigDecimal whtAmount = BigDecimal.ZERO;

        private VatWhtResult(BigDecimal vatAmount, BigDecimal whtAmount) {
            this.vatAmount = vatAmount;
            this.whtAmount = whtAmount;
        }
    }

    //
    public static VatWhtResult calculateVatWhtInclusive(BigDecimal amount, BigDecimal vatRate, BigDecimal whtRate) {
        logger.debug("String incamount="+amount);
        logger.debug("String incvatRate= "+vatRate);
        logger.debug("String incwhtRate="+whtRate);
        
        BigDecimal whtRateVal = whtRate.divide(hundred, 10, R);
        BigDecimal vatRateVal = vatRate.divide(hundred, 10, R);
        logger.debug("Bd whtRateVal="+whtRateVal);
        logger.debug("Bd vatRateVal="+vatRateVal);
        
        // denominator = (1 - WHT%) * (1 + VAT%)
        BigDecimal denominator = (BigDecimal.ONE.subtract(whtRateVal)).multiply(BigDecimal.ONE.add(vatRateVal));
        
        BigDecimal base = amount.abs().divide(denominator, 10, R);
        BigDecimal whtAmount = base.abs().multiply(whtRateVal).setScale(2, R);
        BigDecimal vatAmount = base.abs().multiply(vatRateVal).setScale(2, R);
        logger.debug("Bd vatAmount="+vatAmount);
        logger.debug("Bd whtAmount="+whtAmount);
        
        return new VatWhtResult(vatAmount, whtAmount);
    }

    //
    public static VatWhtResult calculateVatWhtExclusive(BigDecimal baseAmount, BigDecimal vatRate, BigDecimal whtRate) {
        logger.debug("String incbaseAmount="+baseAmount);
        logger.debug("String incvatRate="+vatRate);
        logger.debug("String incwhtRate="+whtRate);
        
        BigDecimal whtRateVal = whtRate.divide(hundred, 10, R);
        BigDecimal vatRateVal = vatRate.divide(hundred, 10, R);
        logger.debug("Bd whtRateVal="+whtRateVal);
        logger.debug("Bd vatRateVal="+vatRateVal);
        
        BigDecimal wht = baseAmount.multiply(whtRateVal).setScale(2, R);
        BigDecimal vatAmount = baseAmount.multiply(vatRateVal).setScale(2, R);
        logger.debug("Bd vatAmount="+vatAmount);
        logger.debug("Bd wht="+wht);
        return new VatWhtResult(vatAmount, wht);
    }

    //
    public static VatWhtResult calculateVatWhtIncExc(BigDecimal netAmount, BigDecimal vatRate, BigDecimal whtRate) {
        logger.debug("String incnetAmount="+netAmount);
        logger.debug("String incvatRate="+vatRate);
        logger.debug("String incwhtRate="+whtRate);
        
        //Input VAT amount = Gross Amount / (1+Input VAT rate%) * Input VAT rate%. 
        BigDecimal one = new BigDecimal("1");
        BigDecimal denominatorWht = one.add(vatRate.divide(hundred, 10, R));
        
        BigDecimal vatAmountVal = netAmount.abs().divide(denominatorWht, 10, R);
        BigDecimal vatAmount = vatAmountVal.multiply(vatRate.divide(hundred, 10, R)).setScale(2, R);
        BigDecimal wht = vatAmountVal.multiply(whtRate.divide(hundred, 10, R)).setScale(2, R);
        logger.debug("Bd vatAmount="+vatAmount);
        logger.debug("Bd wht="+wht);
        
        return new VatWhtResult(vatAmount, wht);
    }

    //
    public static VatWhtResult calculateVatWhtExcInc(BigDecimal netAmount, BigDecimal vatRate, BigDecimal whtRate) {
        logger.debug("String incnetAmount="+netAmount);
        logger.debug("String incvatRate="+vatRate);
        logger.debug("String incwhtRate="+whtRate);
        
        BigDecimal one = new BigDecimal("1");
        BigDecimal denominatorWht = one.subtract(whtRate.divide(hundred, 10, R));
        
        BigDecimal vatAmountVal = netAmount.abs().divide(denominatorWht, 10, R);
        BigDecimal vatAmount = vatAmountVal.multiply(vatRate.divide(hundred, 10, R)).setScale(2, R);
        BigDecimal wht = netAmount.abs().divide(denominatorWht, 2, R).multiply(whtRate.divide(hundred, 2, R))
                .setScale(2, R);
        logger.debug("Bd wht="+wht);
        logger.debug("Bd vatAmount="+vatAmount);

        return new VatWhtResult(vatAmount, wht);
    }
    
    /**
     * WHT INCLUSIVE without VAT (same formula as above – kept because you requested 5 methods)
     */
    public static String calculateWhInc(BigDecimal netAmount, BigDecimal whtRate) {
        logger.debug("String incnetAmount="+netAmount);
        logger.debug("String incwhtRate="+whtRate);
        
        BigDecimal incWhtAmount = BigDecimal.ZERO;
        try {
            BigDecimal ratePercent = whtRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            BigDecimal denominator = BigDecimal.ONE.subtract(ratePercent);
            BigDecimal base = netAmount.divide(denominator, 2, RoundingMode.HALF_UP);
            incWhtAmount = base.multiply(ratePercent).setScale(2, RoundingMode.HALF_UP);
            logger.debug("Bd incWhtAmount="+incWhtAmount);
        } catch (Exception e) {
            //
        }
        return incWhtAmount.toString();
    }
    
    /**
     * WHT INCLUSIVE without VAT (same formula as above – kept because you requested 5 methods)
     */
    public static String calculateWhExc(BigDecimal netAmount, BigDecimal whtRate) {
        logger.debug("String incnetAmount="+netAmount);
        logger.debug("String incwhtRate="+whtRate);
        
        BigDecimal incWhtAmount = BigDecimal.ZERO;
        try {
            BigDecimal whtPercentage = whtRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            incWhtAmount = netAmount.multiply(whtPercentage).setScale(2, RoundingMode.HALF_UP);
            logger.debug("Bd incWhtAmount="+incWhtAmount);
        } catch (Exception e) {
            // 
        }
        return incWhtAmount.toString();
    }
}
