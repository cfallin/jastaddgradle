import org.gradle.api.InvalidUserDataException

class JastAddModule {

	class Aspect {
		String basedir = "."
		List includes = []
		List excludes = []
		List excludeFroms = []
		def basedir(path) {
			basedir = path
		}
		def include(pattern) {
			include pattern, []
		}
		def include(pattern, prio) {
			includes << [ prio, pattern ]
		}
		def exclude(pattern) {
			excludes << pattern
		}
		def excludeFrom(module, pattern) {
			excludeFroms << [ module, pattern ]
		}
	}

	/** all defined modules */
	static List modules = []

	String basedir = "."
	String name
	String moduleName
	String moduleVariant
	Aspect javaAspect = new Aspect()
	Aspect jastaddAspect = new Aspect()
	Aspect scannerAspect = new Aspect()
	Aspect parserAspect = new Aspect()
	def imports = []

	JastAddModule(name) {
		this.name = name
		modules << this
	}

	def imports(name) {
		imports += get(name)
	}

	def "import"(name) {
		imports += get(name)
	}

	def moduleName(version) {
		moduleName = version
	}

	def moduleVariant(version) {
		moduleVariant = version
	}

	static def get(name) {
		for (module in modules) {
			if (module.name == name) {
				return module
			}
		}
		throw new InvalidUserDataException("Unknown module ${name}")
	}

	def java(clos) {
		clos.delegate = javaAspect
		clos.resolveStrategy = Closure.DELEGATE_ONLY
		clos()
	}

	def jastadd(clos) {
		clos.delegate = jastaddAspect
		clos.resolveStrategy = Closure.DELEGATE_ONLY
		clos()
	}

	def scanner(clos) {
		clos.delegate = scannerAspect
		clos.resolveStrategy = Closure.DELEGATE_ONLY
		clos()
	}

	def parser(clos) {
		clos.delegate = parserAspect
		clos.resolveStrategy = Closure.DELEGATE_ONLY
		clos()
	}

	String moduleName() {
		if (moduleName) return moduleName
		for (JastAddModule imp : imports) {
			String value = imp.moduleName()
			if (value) return value
		}
		null
	}

	String moduleVariant() {
		if (moduleVariant) return moduleVariant
		for (JastAddModule imp : imports) {
			String value = imp.moduleVariant()
			if (value) return value
		}
		null
	}

	def includes(component) {
		this."${component}Aspect".includes
	}

	def excludes(component) {
		this."${component}Aspect".excludes
	}

	def excludeFroms(component) {
		this."${component}Aspect".excludeFroms
	}

	File componentBasedir(component) {
		new File(basedir, this."${component}Aspect".basedir)
	}

	def gatherExcludes(component, visited) {
		if (visited.contains(this)) {
			return []
		} else {
			visited << this
		}
		def excludes = excludeFroms(component)
		imports.each{ excludes.addAll(it.gatherExcludes(component, visited)) }
		excludes
	}

	def files(project, component) {
		files(project, component, gatherExcludes(component, [] as Set), [] as Set)
			.sort{ a, b ->
				def aa = a[0]
				def bb = b[0]
				int la = aa.size()
				int lb = bb.size()
				for (int i = 0; i < la || i < lb; ++i) {
					int av = i < la ? aa[i] : 0
					int bv = i < lb ? bb[i] : 0
					if (av != bv) {
						return av <=> bv
					}
				}
				0
			}.collect{ it[1].collect{ it } }.flatten() as LinkedHashSet
		// TODO give warning if there were duplicate files?
	}

	private def files(project, component, excludeFroms, visited) {
		if (visited.contains(this)) {
			return []
		} else {
			visited << this
		}
		def files = []
		imports.each{ files.addAll(it.files(project, component, excludeFroms, visited)) }
		def includes = includes(component)
		def excludes = excludes(component)
		if (includes) {
			def excludeFromsLocal = excludeFroms.findAll{ it[0] == name }
			def localExcludes = excludes + excludeFromsLocal.collect{ it[1] }
			for (include in includes) {
				File base = componentBasedir(component)
				files << [
					include[0],
					project.files(project.fileTree(
						dir: base,
						include: include[1],
						excludes: localExcludes)
					)
				]
			}
		}
		files
	}

}

