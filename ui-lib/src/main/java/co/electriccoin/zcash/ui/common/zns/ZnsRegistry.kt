package co.electriccoin.zcash.ui.common.zns

import cash.z.ecc.android.sdk.model.ZcashNetwork

/**
 * The ZNS registry of one network: its resolver hosts, in the order the ZNS SDK tries them, and the registry viewing
 * key every host must report. The keys are the ones the ZNS TypeScript SDK publishes (`ZNS_NETWORKS` in
 * zcashme/zns-resolver `sdk/typescript/src/zns.ts`).
 */
data class ZnsRegistry(
    val endpoints: List<String>,
    val viewingKey: String,
) {
    companion object {
        val MAINNET =
            ZnsRegistry(
                endpoints = listOf("https://main.zcashnames.com", "https://light.zcash.me/zns-mainnet"),
                viewingKey =
                    "uview1209ld9y4fr2sel9q87kfjg7xm2sy53e3qf7dvcw3w8jwhhjpldl8z0vk0a6hve" +
                        "g4jk7xcn0rc9mr8yg77djfpc0uxrm5pqzt8q7ul4w0nvs3x5q9vmk9nm2crywtqaeale" +
                        "k4mp47ur7ywu7g4g4xcdlwv6gnqzvu5panv5je2ps6rxmx4856unlt5wde8fpz8uhrqc" +
                        "hmwhn2j252h3cyx5phad877xezerm6ex8l5kcfjw02ll7m0nspfufamh3ae9ea48395e" +
                        "lptspnv6vvc70j2gcjrdze9syt8kpr2kc67flvg8687thdkpha7lpt9r4p9wjty90q2v" +
                        "2yjanqsr2zrecrqs7lfx9cpkmshf7jyh2vvwfqnyzwtpuy7p72hm2mjhperdafvm9u5r" +
                        "wa6s6urcy0uw30f687e3jnmas45qfqtwyz39kf6kxklfrdn7t4hkz56jegmlr8s8hwmt" +
                        "9wa8vz8zlmm76lrmzyj82nzpeqfu572njq"
            )

        val TESTNET =
            ZnsRegistry(
                endpoints = listOf("https://light.zcash.me/zns-testnet"),
                viewingKey =
                    "uviewtest1gkq78nsemsvs4qekadrv6qlqdmvcv2cct8k3c8cwycedpk90p8rtkx24qe" +
                        "8q73gg2ark8crqmmaavrerf7cxu0sp50an54crmeaalfxl72aetxnr89ksad4lvxzmnk" +
                        "yejhhwn65euzamt9wsukgfemeayhcesreasarmq600lfeju3qenh05e5e5cz6nhhsw3c" +
                        "fdcd3d08d0nxswxmkkxl2fq0hzv6t5dm3ftnz4y76236a49v3us4uq2tlsewf86tjtfw" +
                        "m6kue348me04rm7dw6uawv8myw7t5tyx2t453clhjz5f55lvh7hnvr8g8zt8lf4sy5rz" +
                        "q23ntp7g5ly6kyz2nkwfek06llhjtuz2fft9rg6cuuwg4n7299vajnmq6swg3sf563r0" +
                        "jjzuwuvfpjhmfmydar9dcu3fkuawt6u8qu08vhg5mthrcggrjuhht6f0perh0tqmdfrt" +
                        "gzvhjymay7h9ymvfxgd4r56uz6zrsf5gnvw5jy"
            )

        fun of(network: ZcashNetwork): ZnsRegistry = if (network == ZcashNetwork.Mainnet) MAINNET else TESTNET
    }
}
