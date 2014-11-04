

/**
 * For a given call-site, get all invocations that match
 * argument descriptions. `argDescrs` contains a closure
 * for each argument.
 * */

Gremlin.defineStep('taintedArgs', [Vertex, Pipe], { argDescrs ->

	// Before we can do anything, we need to generate
	// an initialization graph for the call-site

	_().transform{
		callId = it.id
		tGraph = createInitGraph(callId)
	
		// Check if tainted arg fulfills necessary condition
		// if it doesn't, then we can return an empty set
		if(!canBeTainted(tGraph, argDescrs))
			return []
	
		// necessary condition is fulfilled.
		// now decompress the initialization graph
	
		invocs = decompressInitGraph(tGraph)
		invocs.findAll{ isTainted(it, argDescrs) }
	}.scatter()
})

/**
 * Necessary condition in paper.
 * */

Object.metaClass.canBeTainted = { tGraph, argDescrs ->
	
	// In the future, we want to do this per arg,
	// doesn't matter right now, it's only
	// a necessary condition anyway.
	
	def leaveNodes = tGraph.graphlets.leaves.flatten()
	.collect{ g.v(it) }

	for(it in argDescrs){
		if (leaveNodes.findAll(it) == [])
			return false
	}
	return true
}

/**
 * Sufficient condition in paper
 * */

Object.metaClass.isTainted = { invoc, argDescrs ->
	
	for(int i = 0; i < argDescrs.size(); i++){
		f = argDescrs[i]	
		if(invoc.defStmtsPerArg[i].collect{ g.v(it) }.findAll{ f(it) }.toList() == [])
			return false
	}
	return true
}

Gremlin.defineStep('unchecked', [Vertex,Pipe], { argDescrs ->
	_().transform{ 
				
		it.checksPerArg = genConditionsPerArg(it.allGraphlets, it.graphletIds)
		
		// subtract one because the last one contains conditions unassigned to symbols
		for(int i = 0; i < it.checksPerArg.size() -1; i++){
			f = argDescrs[i]
			syms = it.checksPerArg[i].syms.flatten()
			
			for(int j = 0; j < syms.size(); j++){
				// if one of the sanitizer-descriptions matches, this is sanitized, so return []
				X = it.checksPerArg[i].flatten().cndId.collect{ g.v(it) }
					.findAll{ x -> f(x, syms[j]) }
				if( X != []) return []
			}
			
		}
		// none of the sanitizer-descriptions matched
		return [it]
	}.scatter()
})
