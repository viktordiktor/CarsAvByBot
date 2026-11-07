package com.avby.avby.avby;

import com.avby.avby.domain.FilterProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvbyFilterUrlBuilderTest {

    private final AvbyFilterUrlBuilder builder = new AvbyFilterUrlBuilder("https://cars.av.by");

    @Test
    void buildsFullFilterUrl() {
        FilterProfile profile = new FilterProfile();
        profile.setSellerType(1);
        profile.setKeyword("срочно");
        profile.setPriceMinUsd(2000);
        profile.setPriceMaxUsd(4000);
        profile.setYearMin(2010);
        profile.setYearMax(2015);
        profile.setMileageMinKm(100000);
        profile.setMileageMaxKm(200000);
        profile.setTransmissionType("1,3,4");
        profile.setBodyType("5");
        profile.setEngineType("5");
        profile.setDriveType("3");
        profile.setBrandId("6");
        profile.setRegion("1001");
        profile.setColor("12");

        String url = builder.build(profile, 1);

        assertThat(url).startsWith("https://cars.av.by/filter?");
        assertThat(url).contains("seller_type[0]=1");
        assertThat(url).contains("description=%D1%81%D1%80%D0%BE%D1%87%D0%BD%D0%BE");
        assertThat(url).contains("price_usd[min]=2000");
        assertThat(url).contains("price_usd[max]=4000");
        assertThat(url).contains("price_currency=2");
        assertThat(url).contains("year[min]=2010");
        assertThat(url).contains("year[max]=2015");
        assertThat(url).contains("mileage_km[min]=100000");
        assertThat(url).contains("mileage_km[max]=200000");
        assertThat(url).contains("transmission_type[0]=1&transmission_type[1]=3&transmission_type[2]=4");
        assertThat(url).contains("body_type[0]=5");
        assertThat(url).contains("engine_type[0]=5");
        assertThat(url).contains("drive_type[0]=3");
        assertThat(url).contains("brands[0][brand]=6");
        assertThat(url).contains("place_region[0]=1001");
        assertThat(url).contains("color[0]=12");
    }

    @Test
    void addsPageAndExtraQuery() {
        FilterProfile profile = new FilterProfile();
        profile.setSellerType(1);
        profile.setKeyword("срочно");
        profile.setExtraQuery("?has_photo360=1&condition[4]=5");

        String url = builder.build(profile, 3);

        assertThat(url).contains("page=3");
        assertThat(url).contains("has_photo360=1");
        assertThat(url).contains("condition[4]=5");
        assertThat(url).doesNotContain("??");
    }

    @Test
    void omitsEmptyValues() {
        FilterProfile profile = new FilterProfile();
        profile.setSellerType(1);
        profile.setKeyword(null);

        String url = builder.build(profile, 1);

        assertThat(url).doesNotContain("description=");
        assertThat(url).doesNotContain("price_usd");
        assertThat(url).doesNotContain("page=");
    }
}
