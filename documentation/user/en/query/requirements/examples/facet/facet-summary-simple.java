final EvitaResponse<SealedEntity> entities = evita.queryCatalog(
	"evita",
	session -> {
		return session.querySealedEntity(
			query(
				collection("Product"),
				filterBy(
					hierarchyWithin(
						"categories",
						attributeEquals("code", "accessories")
					),
					attributeEquals("status", "ACTIVE"),
					entityLocaleEquals(Locale.forLanguageTag("en"))
				),
				require(
					facetSummary(
						COUNTS,
						entityFetch(
							attributeContent("name")
						),
						entityGroupFetch(
							attributeContent("name")
						)
					)
				)
			)
		);
	}
);