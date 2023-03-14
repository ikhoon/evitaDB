/*
 *
 *                         _ _        ____  ____
 *               _____   _(_) |_ __ _|  _ \| __ )
 *              / _ \ \ / / | __/ _` | | | |  _ \
 *             |  __/\ V /| | || (_| | |_| | |_) |
 *              \___| \_/ |_|\__\__,_|____/|____/
 *
 *   Copyright (c) 2023
 *
 *   Licensed under the Business Source License, Version 1.1 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://github.com/FgForrest/evitaDB/blob/main/LICENSE
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

with recursive hier(primaryKey, parentEntityPrimaryKey, depth, path) as (
        select primaryKey, parententityprimarykey, 1, ARRAY[primarykey]
        from t_entity
        where parententityprimarykey is null
    union all
        select e.primaryKey, e.parentEntityPrimaryKey, pe.depth + 1, pe.path || e.id
        from t_entity e, hier pe
        where pe.primarykey = e.parentEntityPrimaryKey
)
select * from hier order by path;
