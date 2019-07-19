package gov.ita.terrafreights.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("development")
public class DevelopmentStorageInitializer implements StorageInitializer {
  @Override
  public void init() {

  }
}