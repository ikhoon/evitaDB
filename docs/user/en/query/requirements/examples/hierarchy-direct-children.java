final EvitaResponse<SealedEntity> result = session.querySealedEntity(
	query(
		// query hierarchy entity type
		collection("Product"),
		// target "Accessories" category
		filterBy(
			hierarchyWithin(
				"categories",
				attributeEquals("code", "audio")
			)
		),
		require(
			hierarchyOfSelf(
				// request computation of direct children of the Audio category
				children(
					"subcategories",
					entityFetch(attributeContent(code)),
					stopAt(distance(1))
				)
			)
		)
	)
);