package gov.ita.terrafreights.storage;

import com.microsoft.azure.storage.blob.*;
import com.microsoft.azure.storage.blob.models.BlobHTTPHeaders;
import com.microsoft.azure.storage.blob.models.ContainerItem;
import com.microsoft.azure.storage.blob.models.PublicAccessType;
import com.microsoft.rest.v2.http.HttpPipeline;
import com.microsoft.rest.v2.util.FlowableUtil;
import gov.ita.terrafreights.TerraFreightsInitializer;
import gov.ita.terrafreights.country.Country;
import gov.ita.terrafreights.country.CountryList;
import gov.ita.terrafreights.tariff.TariffBlobMetadata;
import io.reactivex.Flowable;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Profile({"production", "staging"})
public class ProductionStorage implements Storage {

  @Autowired
  private RestTemplate restTemplate;

  @Value("${terrafreights.azure-storage-account}")
  private String accountName;

  @Value("${terrafreights.azure-storage-account-key}")
  private String accountKey;

  @Value("${terrafreights.azure-storage-container}")
  private String containerName;

  @Override
  public void save(String fileName, String fileContent, String contentType, String user) {
    ContainerURL containerURL = makeContainerUrl();
    BlockBlobURL blobURL = containerURL.createBlockBlobURL(fileName);
    blobURL.upload(Flowable.just(ByteBuffer.wrap(fileContent.getBytes())), fileContent.getBytes().length,
      makeHeader(contentType), makeMetaData(user), null, null)
      .flatMap(blobsDownloadResponse ->
        blobURL.download())
      .flatMap(blobsDownloadResponse ->
        FlowableUtil.collectBytesInBuffer(blobsDownloadResponse.body(null))
          .doOnSuccess(byteBuffer -> {
            if (byteBuffer.compareTo(ByteBuffer.wrap(fileContent.getBytes())) != 0) {
              throw new Exception("The downloaded data does not match the uploaded data.");
            }
          }))
      .blockingGet();
  }

  @Override
  public boolean containerExists() {
    ServiceURL serviceURL = makeServiceURL();
    assert serviceURL != null;
    List<ContainerItem> containerItems = serviceURL.listContainersSegment(null, null)
      .blockingGet().body().containerItems();
    return containerItems.stream().anyMatch(containerItem -> containerItem.name().equals(containerName));
  }

  @Override
  public void createContainer() {
    try {
      ContainerURL containerURL = makeContainerUrl();
      BlockBlobURL blobURL = containerURL.createBlockBlobURL("countries.json");
      InputStream in = TerraFreightsInitializer.class.getResourceAsStream("/fixtures/countries.json");
      String data = IOUtils.toString(new InputStreamReader(in));

      containerURL
        .create(makeMetaData(accountName), PublicAccessType.BLOB, null)
        .flatMap(containerCreateResponse ->
          blobURL.upload(Flowable.just(ByteBuffer.wrap(data.getBytes())), data.getBytes().length,
            makeHeader("application/json"), makeMetaData(accountName), null, null)
        )
        .flatMap(blobsDownloadResponse ->
          blobURL.download())
        .flatMap(blobsDownloadResponse ->
          FlowableUtil.collectBytesInBuffer(blobsDownloadResponse.body(null))
            .doOnSuccess(byteBuffer -> {
              if (byteBuffer.compareTo(ByteBuffer.wrap(data.getBytes())) != 0) {
                throw new Exception("The downloaded data does not match the uploaded data.");
              }
            }))
        .blockingGet();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public List<Country> getCountries() {
    String url = String.format("https://%s.blob.core.windows.net/%s/countries.json", accountName, containerName);
    return Objects.requireNonNull(restTemplate.getForObject(url, CountryList.class)).getCountries();
  }

  @Override
  public List<TariffBlobMetadata> getBlobsMetadata(String prefix) {
    ListBlobsOptions listBlobsOptions = new ListBlobsOptions();
    listBlobsOptions.withPrefix(prefix);
    BlobListDetails details = new BlobListDetails();
    details.withMetadata(true);
    listBlobsOptions.withDetails(details);
    List<TariffBlobMetadata> meta = makeContainerUrl()
      .listBlobsFlatSegment(null, listBlobsOptions, null).blockingGet().body().segment()
      .blobItems()
      .stream().map(
        x -> new TariffBlobMetadata(
          buildUrlForBlob(x.name()),
          x.metadata().get("uploaded_by"),
          x.properties().lastModified().toLocalDateTime()
        ))
      .collect(Collectors.toList());

    String latestBloUrl = meta.stream().max(Comparator.comparing(TariffBlobMetadata::getUploadedAt)).get().getUrl();
    return meta.stream().peek(x -> {
      if (x.getUrl().equals(latestBloUrl)) x.setLatestUpload(true);
    }).collect(Collectors.toList());
  }

  @Override
  public ResponseEntity<byte[]> getLatestBlobByCountry(String prefix) {
    List<TariffBlobMetadata> blobsMetadata = getBlobsMetadata(prefix);
    TariffBlobMetadata latest = blobsMetadata.stream().filter(TariffBlobMetadata::isLatestUpload).findFirst().get();

    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));
    HttpEntity<String> entity = new HttpEntity<>(headers);

    return restTemplate.exchange(latest.getUrl(), HttpMethod.GET, entity, byte[].class);
  }

  private String buildUrlForBlob(String blobName) {
    return String.format("https://%s.blob.core.windows.net/%s/%s", accountName, containerName, blobName);
  }

  private ContainerURL makeContainerUrl() {
    ServiceURL serviceURL = makeServiceURL();
    assert serviceURL != null;
    return serviceURL.createContainerURL(containerName);
  }

  private ServiceURL makeServiceURL() {
    try {
      SharedKeyCredentials credential = new SharedKeyCredentials(accountName, accountKey);
      HttpPipeline pipeline = StorageURL.createPipeline(credential, new PipelineOptions());
      URL url = new URL(String.format("https://%s.blob.core.windows.net/", accountName));
      return new ServiceURL(url, pipeline);
    } catch (InvalidKeyException | MalformedURLException e) {
      e.printStackTrace();
    }
    return null;
  }

  private Metadata makeMetaData(String user) {
    Metadata metadata = new Metadata();
    metadata.put("uploaded_by", user);
    return metadata;
  }

  private BlobHTTPHeaders makeHeader(String contentType) {
    BlobHTTPHeaders headers = new BlobHTTPHeaders();
    headers.withBlobContentType(contentType);
    return headers;
  }
}
