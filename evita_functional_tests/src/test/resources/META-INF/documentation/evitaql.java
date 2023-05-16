/* INIT START */
var evita = new EvitaClient(
	EvitaClientConfiguration.builder()
		.host("demo.evitadb.io")
		.port(5556)
        // demo server provides Let's encrypt trusted certificate
	.useGeneratedCertificate(false)
        // the client will not be mutually verified by the server side
        .mtlsEnabled(false)
		.build()
);
/* INIT END */
final EvitaResponse<SealedEntity> entities = evita.queryCatalog(
	"evita",
	session -> {
		return session.querySealedEntity(
			#QUERY#
		);
	}
);