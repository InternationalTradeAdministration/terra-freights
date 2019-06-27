package gov.ita.terrafreights.tariff;

import gov.ita.terrafreights.tariff.stagingbasket.StagingBasket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TariffController {

  private TariffRepository tariffRepository;

  public TariffController(TariffRepository tariffRepository) {
    this.tariffRepository = tariffRepository;
  }

  @GetMapping("/api/tariffs")
  public Page<Tariff> tariffs(Pageable pageable,
                              @RequestParam("countryCode") String countryCode,
                              @RequestParam("stagingBasketId") Long stagingBasketId) {
    if (stagingBasketId != -1)
      return tariffRepository.findByCountryCodeAndStagingBasketId(countryCode, stagingBasketId, pageable);

    return tariffRepository.findByCountryCode(countryCode, pageable);
  }

  @GetMapping("/api/staging_baskets")
  public List<StagingBasket> stagingBaskets(@RequestParam("countryCode") String countryCode) {
    return tariffRepository.findAllStagingBasketsByCountry(countryCode);
  }
}
