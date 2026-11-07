package com.avby.avby.web;

import java.util.ArrayList;
import java.util.List;

/**
 * Value/label pairs for av.by filter options. Values are the ids that av.by
 * puts into the query string (for example {@code transmission_type[0]=2}).
 */
public final class FilterOptions {

    private FilterOptions() {
    }

    public record Option(String value, String label) {
    }

    public static final List<Option> SELLER_TYPES = List.of(
            new Option("", "Все продавцы"),
            new Option("1", "Частные лица"),
            new Option("2", "Компании и дилеры")
    );

    /**
     * Brand ids from the av.by filter dictionary. Sent as
     * {@code brands[0][brand]=<id>}. Models depend on the brand and are not
     * listed here; use the extra query field for a specific model.
     */
    public static final List<Option> BRANDS = List.of(
            new Option("10297", "Abarth"),
            new Option("1444", "Acura"),
            new Option("10423", "Aito"),
            new Option("1", "Alfa Romeo"),
            new Option("5940", "Alpina"),
            new Option("10651", "Arcfox"),
            new Option("5772", "Asia"),
            new Option("2325", "Aston Martin"),
            new Option("6", "Audi"),
            new Option("10346", "Avatr"),
            new Option("10034", "BAIC"),
            new Option("10337", "Baojun"),
            new Option("10530", "BAW"),
            new Option("10457", "Belgee"),
            new Option("1676", "Bentley"),
            new Option("8", "BMW"),
            new Option("2210", "Brilliance"),
            new Option("1506", "Buick"),
            new Option("5459", "BYD"),
            new Option("40", "Cadillac"),
            new Option("2632", "Changan"),
            new Option("1998", "Chery"),
            new Option("41", "Chevrolet"),
            new Option("42", "Chrysler"),
            new Option("43", "Citroen"),
            new Option("10236", "Cupra"),
            new Option("1841", "Dacia"),
            new Option("46", "Daewoo"),
            new Option("47", "Daihatsu"),
            new Option("2578", "Datsun"),
            new Option("10287", "Denza"),
            new Option("10824", "DFSK"),
            new Option("45", "Dodge"),
            new Option("5780", "Dongfeng"),
            new Option("10106", "Dongfeng Honda"),
            new Option("10563", "DS"),
            new Option("10877", "Epai"),
            new Option("10303", "EXEED"),
            new Option("10572", "FangChengBao"),
            new Option("10761", "Farizon"),
            new Option("2465", "FAW"),
            new Option("288", "Ferrari"),
            new Option("301", "Fiat"),
            new Option("2323", "Fisker"),
            new Option("330", "Ford"),
            new Option("2355", "Foton"),
            new Option("10131", "GAC"),
            new Option("2012", "Geely"),
            new Option("10006", "Genesis"),
            new Option("372", "GMC"),
            new Option("1726", "Great Wall"),
            new Option("2215", "Hafei"),
            new Option("5070", "Haima"),
            new Option("5782", "Haval"),
            new Option("10279", "HiPhi"),
            new Option("383", "Honda"),
            new Option("10275", "Hongqi"),
            new Option("10259", "Hozon"),
            new Option("1498", "Hummer"),
            new Option("10326", "Hycan"),
            new Option("433", "Hyundai"),
            new Option("1343", "Infiniti"),
            new Option("2022", "Iran Khodro"),
            new Option("461", "Isuzu"),
            new Option("10813", "IVECO"),
            new Option("2030", "JAC"),
            new Option("526", "Jaguar"),
            new Option("540", "Jeep"),
            new Option("10362", "Jetour"),
            new Option("10450", "Jetta"),
            new Option("10548", "Jiyue"),
            new Option("10517", "Jmev"),
            new Option("10463", "Kaiyi"),
            new Option("10612", "Karma"),
            new Option("10930", "KGM"),
            new Option("545", "Kia"),
            new Option("1279", "Lada (ВАЗ)"),
            new Option("2437", "Lamborghini"),
            new Option("572", "Lancia"),
            new Option("584", "Land Rover"),
            new Option("10268", "Leapmotor"),
            new Option("589", "Lexus"),
            new Option("10209", "Li Auto"),
            new Option("2586", "Lifan"),
            new Option("601", "Lincoln"),
            new Option("10907", "Lingxi"),
            new Option("10519", "Livan"),
            new Option("2295", "Lotus"),
            new Option("10998", "Lucid"),
            new Option("10471", "Lynk & Co"),
            new Option("10733", "Maple"),
            new Option("1625", "Maserati"),
            new Option("10592", "Maxus"),
            new Option("634", "Mazda"),
            new Option("5970", "McLaren"),
            new Option("683", "Mercedes-Benz"),
            new Option("825", "Mercury"),
            new Option("1906", "MG"),
            new Option("10510", "MHERO"),
            new Option("1850", "MINI"),
            new Option("834", "Mitsubishi"),
            new Option("10411", "Nio"),
            new Option("892", "Nissan"),
            new Option("1364", "Oldsmobile"),
            new Option("10483", "Omoda"),
            new Option("966", "Opel"),
            new Option("10727", "Oting"),
            new Option("989", "Peugeot"),
            new Option("1012", "Plymouth"),
            new Option("10042", "Polestar"),
            new Option("1022", "Pontiac"),
            new Option("1485", "Porsche"),
            new Option("1609", "Proton"),
            new Option("10226", "RAM"),
            new Option("5503", "Ravon"),
            new Option("1039", "Renault"),
            new Option("10100", "Renault Samsung"),
            new Option("10624", "Rivian"),
            new Option("5800", "Roewe"),
            new Option("1067", "Rover"),
            new Option("10836", "Rox"),
            new Option("1085", "Saab"),
            new Option("5029", "Saipa"),
            new Option("1703", "Saturn"),
            new Option("2698", "Scion"),
            new Option("1091", "SEAT"),
            new Option("10289", "SERES"),
            new Option("10545", "Shenlan (Deepal)"),
            new Option("10447", "Shineray"),
            new Option("1126", "Skoda"),
            new Option("10308", "Skywell"),
            new Option("2449", "Smart"),
            new Option("1597", "SsangYong"),
            new Option("10759", "Stelato"),
            new Option("1136", "Subaru"),
            new Option("1155", "Suzuki"),
            new Option("10620", "Tank"),
            new Option("2521", "Tesla"),
            new Option("1181", "Toyota"),
            new Option("1216", "Volkswagen"),
            new Option("1238", "Volvo"),
            new Option("5437", "Vortex"),
            new Option("10244", "Voyah"),
            new Option("1857", "Wartburg"),
            new Option("10067", "Weltmeister"),
            new Option("10617", "Wey"),
            new Option("10334", "Wuling"),
            new Option("10584", "Xiaomi"),
            new Option("6019", "Xpeng"),
            new Option("10185", "Zeekr"),
            new Option("2510", "Zotye"),
            new Option("5066", "ZX"),
            new Option("5076", "Богдан"),
            new Option("1310", "ГАЗ"),
            new Option("10094", "ЕрАЗ"),
            new Option("1551", "ЗАЗ"),
            new Option("2894", "ИЖ"),
            new Option("2345", "ЛуАЗ"),
            new Option("2051", "Москвич"),
            new Option("5252", "РАФ"),
            new Option("5032", "ТагАЗ"),
            new Option("1464", "УАЗ"),
            new Option("5019", "Эксклюзив")
    );

    public static final List<Option> TRANSMISSION_TYPES = List.of(
            new Option("1", "Автоматическая"),
            new Option("2", "Механическая"),
            new Option("3", "Робот"),
            new Option("4", "Вариатор")
    );

    public static final List<Option> BODY_TYPES = List.of(
            new Option("5", "Седан"),
            new Option("2", "Универсал"),
            new Option("3", "Хэтчбек 5 дв."),
            new Option("24", "Хэтчбек 3 дв."),
            new Option("26", "Лифтбек"),
            new Option("4", "Минивэн"),
            new Option("6", "Внедорожник 5 дв."),
            new Option("23", "Внедорожник 3 дв."),
            new Option("1", "Купе"),
            new Option("7", "Кабриолет"),
            new Option("18", "Родстер"),
            new Option("8", "Пикап"),
            new Option("22", "Лимузин"),
            new Option("11", "Микроавтобус пассажирский"),
            new Option("21", "Микроавтобус грузопассажирский"),
            new Option("20", "Легковой фургон"),
            new Option("19", "Другой")
    );

    public static final List<Option> ENGINE_TYPES = List.of(
            new Option("1", "Бензин"),
            new Option("2", "Бензин (пропан-бутан)"),
            new Option("3", "Бензин (метан)"),
            new Option("4", "Бензин (гибрид)"),
            new Option("5", "Дизель"),
            new Option("6", "Дизель (гибрид)"),
            new Option("7", "Электро")
    );

    public static final List<Option> DRIVE_TYPES = List.of(
            new Option("1", "Передний привод"),
            new Option("2", "Задний привод"),
            new Option("3", "Подключаемый полный привод"),
            new Option("4", "Постоянный полный привод")
    );

    public static final List<Option> REGIONS = List.of(
            new Option("1001", "Брестская обл."),
            new Option("1002", "Витебская обл."),
            new Option("1003", "Гомельская обл."),
            new Option("1004", "Гродненская обл."),
            new Option("1005", "Минская обл."),
            new Option("1006", "Могилёвская обл.")
    );

    public static final List<Option> COLORS = List.of(
            new Option("1", "Белый"),
            new Option("2", "Бордовый"),
            new Option("3", "Жёлтый"),
            new Option("4", "Зелёный"),
            new Option("5", "Коричневый"),
            new Option("6", "Красный"),
            new Option("7", "Оранжевый"),
            new Option("8", "Серебристый"),
            new Option("9", "Серый"),
            new Option("10", "Синий"),
            new Option("11", "Фиолетовый"),
            new Option("12", "Чёрный"),
            new Option("13", "Другой")
    );

    public static List<Option> years() {
        int currentYear = java.time.Year.now().getValue();
        List<Option> years = new ArrayList<>();
        for (int year = currentYear; year >= 1950; year--) {
            years.add(new Option(String.valueOf(year), String.valueOf(year)));
        }
        return years;
    }
}
