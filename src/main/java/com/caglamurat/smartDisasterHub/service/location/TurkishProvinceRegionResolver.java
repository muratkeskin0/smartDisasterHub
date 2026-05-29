package com.caglamurat.smartDisasterHub.service.location;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps Turkish province (il) names — as returned by Nominatim {@code address.state} —
 * to coarse region keys used in reports ({@code marmara}, {@code aegean}, …).
 * Classification follows TÜİK geographical regions (7 regions).
 */
@Component
public class TurkishProvinceRegionResolver {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final Map<String, String> NORMALIZED_IL_TO_REGION = new HashMap<>();

    static {
        // Marmara
        putIl("Balıkesir", "marmara");
        putIl("Bilecik", "marmara");
        putIl("Bursa", "marmara");
        putIl("Çanakkale", "marmara");
        putIl("Edirne", "marmara");
        putIl("İstanbul", "marmara");
        putIl("Kırklareli", "marmara");
        putIl("Kocaeli", "marmara");
        putIl("Sakarya", "marmara");
        putIl("Tekirdağ", "marmara");
        putIl("Yalova", "marmara");
        // Ege
        putIl("Afyonkarahisar", "aegean");
        putIl("Aydın", "aegean");
        putIl("Denizli", "aegean");
        putIl("İzmir", "aegean");
        putIl("Kütahya", "aegean");
        putIl("Manisa", "aegean");
        putIl("Muğla", "aegean");
        putIl("Uşak", "aegean");
        // Akdeniz
        putIl("Adana", "mediterranean");
        putIl("Antalya", "mediterranean");
        putIl("Burdur", "mediterranean");
        putIl("Hatay", "mediterranean");
        putIl("Isparta", "mediterranean");
        putIl("Kahramanmaraş", "mediterranean");
        putIl("Mersin", "mediterranean");
        putIl("Osmaniye", "mediterranean");
        // İç Anadolu
        putIl("Aksaray", "central");
        putIl("Ankara", "central");
        putIl("Çankırı", "central");
        putIl("Eskişehir", "central");
        putIl("Karaman", "central");
        putIl("Kayseri", "central");
        putIl("Kırıkkale", "central");
        putIl("Kırşehir", "central");
        putIl("Konya", "central");
        putIl("Nevşehir", "central");
        putIl("Niğde", "central");
        putIl("Sivas", "central");
        putIl("Yozgat", "central");
        // Karadeniz
        putIl("Amasya", "black_sea");
        putIl("Artvin", "black_sea");
        putIl("Bartın", "black_sea");
        putIl("Bayburt", "black_sea");
        putIl("Bolu", "black_sea");
        putIl("Çorum", "black_sea");
        putIl("Düzce", "black_sea");
        putIl("Giresun", "black_sea");
        putIl("Gümüşhane", "black_sea");
        putIl("Karabük", "black_sea");
        putIl("Kastamonu", "black_sea");
        putIl("Ordu", "black_sea");
        putIl("Rize", "black_sea");
        putIl("Samsun", "black_sea");
        putIl("Sinop", "black_sea");
        putIl("Tokat", "black_sea");
        putIl("Trabzon", "black_sea");
        putIl("Zonguldak", "black_sea");
        // Doğu Anadolu
        putIl("Ağrı", "east_anatolia");
        putIl("Ardahan", "east_anatolia");
        putIl("Bingöl", "east_anatolia");
        putIl("Bitlis", "east_anatolia");
        putIl("Elazığ", "east_anatolia");
        putIl("Erzincan", "east_anatolia");
        putIl("Erzurum", "east_anatolia");
        putIl("Hakkâri", "east_anatolia");
        putIl("Iğdır", "east_anatolia");
        putIl("Kars", "east_anatolia");
        putIl("Malatya", "east_anatolia");
        putIl("Muş", "east_anatolia");
        putIl("Tunceli", "east_anatolia");
        putIl("Van", "east_anatolia");
        // Güneydoğu Anadolu
        putIl("Adıyaman", "southeast_anatolia");
        putIl("Batman", "southeast_anatolia");
        putIl("Diyarbakır", "southeast_anatolia");
        putIl("Gaziantep", "southeast_anatolia");
        putIl("Kilis", "southeast_anatolia");
        putIl("Mardin", "southeast_anatolia");
        putIl("Siirt", "southeast_anatolia");
        putIl("Şanlıurfa", "southeast_anatolia");
        putIl("Şırnak", "southeast_anatolia");
        // OSM / geocoder spelling variants
        NORMALIZED_IL_TO_REGION.put(normKey("Hakkari"), "east_anatolia");
        NORMALIZED_IL_TO_REGION.put(normKey("Afyon Karahisar"), "aegean");
    }

    private static void putIl(String officialIlName, String regionKey) {
        NORMALIZED_IL_TO_REGION.put(normKey(officialIlName), regionKey);
    }

    private static String normKey(String il) {
        if (il == null) {
            return "";
        }
        return il.trim().toLowerCase(TR);
    }

    /**
     * @return region key compatible with report i18n (e.g. {@code marmara}), or null if unknown / not Turkey.
     */
    public String regionKeyForProvince(String provinceState, String countryCode) {
        if (provinceState == null || provinceState.isBlank()) {
            return null;
        }
        if (countryCode == null || !"tr".equalsIgnoreCase(countryCode.trim())) {
            return null;
        }
        return NORMALIZED_IL_TO_REGION.get(normKey(provinceState));
    }
}
