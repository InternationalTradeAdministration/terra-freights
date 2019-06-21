package gov.ita.terrafreights.tariff;

import gov.ita.terrafreights.country.Country;
import gov.ita.terrafreights.product.ProductType;
import gov.ita.terrafreights.stagingbasket.StagingBasket;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Service
public class TariffCsvTranslator {

  public List<Tariff> translate(String countryCode, String csv) {
    CSVParser csvParser;
    Reader reader = new StringReader(csv);
    List<Tariff> tariffs = new ArrayList<>();

    try {
      csvParser = new CSVParser(
        reader,
        CSVFormat.DEFAULT
          .withFirstRecordAsHeader()
          .withTrim()
          .withNullString("")
      );

      for (CSVRecord csvRecord : csvParser) {
        Tariff tf = Tariff.builder()
          .legacyId(Long.parseLong(csvRecord.get("ID")))
          .tariffLine(csvRecord.get("TL"))
          .description(csvRecord.get("TL_Desc"))
          .sectorCode(csvRecord.get("Sector_Code"))
          .baseRate(csvRecord.get("Base_Rate"))
          .baseRateAlt(csvRecord.get("Base_Rate_Alt"))
          .finalYear(intParser(csvRecord.get("Final_Year")))
          .tariffRateQuota(intParser(csvRecord.get("TRQ_Quota")))
          .tariffRateQuotaNotes(csvRecord.get("TRQ_Note"))
          .tariffEliminated(Boolean.parseBoolean(csvRecord.get("Tariff_Eliminated")))
          .partnerName(csvRecord.get("PartnerName"))
          .reporterName(csvRecord.get("ReporterName"))
          .partnerStartYear(intParser(csvRecord.get("PartnerStartYear")))
          .reporterStartYear(intParser(csvRecord.get("ReporterStartYear")))
          .partnerAgreementName(csvRecord.get("PartnerAgreementName"))
          .reporterAgreementName(csvRecord.get("ReporterAgreementName"))
          .quotaName(csvRecord.get("QuotaName"))
          .ruleText(csvRecord.get("Rule_Text"))
          .linkText(csvRecord.get("Link_Text"))
          .linkUrl(csvRecord.get("Link_Url"))
          .country(new Country(null, countryCode, null))
          .hs6(new HS6(
            csvRecord.get("HS6"),
            csvRecord.get("HS6_Desc")
          ))
          .stagingBasket(new StagingBasket(
            null,
            Long.parseLong(csvRecord.get("StagingBasketId")),
            csvRecord.get("StagingBasket")
          ))
          .productType(new ProductType(
            null,
            Long.parseLong(csvRecord.get("Product_Type")),
            csvRecord.get("ProductType")
          )).build();

        List<Rate> rates = new ArrayList<>();
        if (countryCode.contains("USMCA")) {
          for (int i = 1; i <= 30; i++) {
            String value = csvRecord.get("YEAR" .concat(String.valueOf(i)));
            String alt = csvRecord.get("YEAR" .concat(String.valueOf(i).concat("_Alt")));
            if (alt != null) {
              rates.add(new Rate(i, alt));
            } else if (value != null && doubleParser(value) != 0) {
              rates.add(new Rate(i, value));
            }
          }
        } else {
          for (int i = 2004; i <= 2041; i++) {
            String value = csvRecord.get("Y" .concat(String.valueOf(i)));
            String alt = csvRecord.get("Alt_" .concat(String.valueOf(i)));
            if (alt != null) {
              rates.add(new Rate(i, alt));
            } else if (value != null && doubleParser(value) != 0) {
              rates.add(new Rate(i, value));
            }
          }
        }

        tf.setRates(rates);

        tariffs.add(tf);
      }

    } catch (IOException e) {
      e.printStackTrace();
    }

    return tariffs;
  }

  private Integer intParser(String potentialInteger) {
    if (potentialInteger == null) return null;
    try {
      return Integer.parseInt(potentialInteger);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private Double doubleParser(String potentialDouble) {
    if (potentialDouble == null) return null;
    return Double.parseDouble(potentialDouble);
  }

}
