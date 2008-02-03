Conductor : Environment {
	var <>valueKeys, <path;
	var <>gui;			// defines gui display of conductor in windows
	var <>player;
	var <>preset;
	
	*make { arg func; 
		var obj, args, names;
		obj = this.new;
		^obj.make(func)
	}
	
	*new { ^super.new.init }
	
	init {
		gui = ConductorGUI(this, #[ ]);
		this[\player] = player = ConductorPlayer(this);
	}
			
	make { arg func; 
		var obj, args, names;


		#args, names = this.makeArgs(func);
		valueKeys = valueKeys ++ names;
		gui.keys_( gui.keys ++ names);
		this.usePresets;
		super.make({func.valueArray(this, args)});
	}


	makeArgs { arg func;
		var argList, size, names, argNames;
		var theClassName, name, obj;
		
		size = func.def.argNames.size;
		argList = Array(size);
		argNames = Array(size);
		names = func.def.argNames;
		// first arg is Event under constructions, subsequent are CVs or instances of other classes
		if (size > 1, {
			1.forBy(size - 1, 1, { arg i;
				name = names.at(i).asSymbol;
				argNames = argNames.add(name);
				theClassName = func.def.prototypeFrame.at(i) ? \CV;
				obj = theClassName.asClass.new;
				this.put(name,obj);
				argList = argList.add(obj);
			});
		});
		^[argList, argNames];
		
	}

// saving and restoring state 
	getFile { arg argPath; var file, contents;
		if (File.exists(argPath)) {
			path = argPath;
			file = File(path,"r"); 
			contents = file.readAllStringRTF;
			file.close;
			^contents;
		} {
			(argPath + "not found").postln;
			^nil
		}
	}
	
	putFile { | vals, argPath | 
		path = argPath ? path;
		File(path,"w").putAll(vals).close;
	}

	load { | argPath |
		var v;
		if (argPath.isNil) {
			File.openDialog(nil, { arg path; 
				v = this.getFile(path);
				this.value_(v.interpret)
			});
		} {
			v = this.getFile(argPath);
			this.value_(v.interpret)
		};
	}
	
	save { | path |
		if (path.isNil) {
			File.saveDialog(nil, nil, { arg path; 
				this.putFile(this.value.asCompileString, path)
			});
		} {
			this.putFile(this.value.asCompileString, path)
		};

	}

	path_ { | path |
		this.load(path);
	}

// gui display of file saving
	noSettings { this[\settings] = nil; this[\preset] = nil; }
	
	useSettings { 
		this[\settings] = ConductorSettingsGUI(this);
		this[\preset] = nil;
	}
	
	usePresets { 
		this[\settings] = ConductorSettingsGUI(this);
		this[\preset] = preset =  CVPreset.new; 
		this.presetKeys_(valueKeys);		
	}
	
	useInterpolator { 
		this[\settings] = ConductorSettingsGUI(this);
		this[\preset] = preset =  CVInterpolator.new; 
		this.presetKeys_(valueKeys);
		this.interpKeys_(valueKeys);
	}
	
// interface to default preset and interpolator

	presetKeys_ { | keys, argPreset |
		argPreset = argPreset ? preset;
		preset.items = keys.collect { | k | this[k] };
	}
	
	interpKeys_ { | keys, argPreset |
		argPreset = argPreset ? preset;
		argPreset.interpItems = keys.collect { | k | this[k] };
	}

//	input {  ^preset.input	}
//	input_ { | in | preset.input_(in) }
	input {  var keys;
		if (this[\preset].notNil) { keys = #[preset] };
		^(valueKeys ++ keys).collect { | k| [k, this[k].input ]  }  }
		
	input_ { | kvs | kvs.do { | kv| this[kv[0]].input_(kv[1]); kv[0]; } }
	
	value {  ^(valueKeys ++ #[preset]).collect { | k| [k, this[k].value ]  }  }
	
	value_ { | kvs | kvs.do { | kv| this[kv[0]].value_(kv[1]); kv[0]; } }
	
//gui interface
	show { arg argName, x = 128, y = 64, w = 900, h = 160;
//		var win, bounds;
//		
//		win = SCWindow(argName ? "", Rect(x,y,w,h));
//		win.view.decorator = FlowLayout(win.view.bounds);
//		bounds = win.bounds;
//
//		this.draw(win, this.name, this);
//		win.view.decorator.nextLine;
//		bounds.height = win.view.decorator.top + 16;
//		win.bounds_(bounds);
//		win.front;
//		this[\win] = win;
//		^win;
		^gui.show(argName, x, y, w, h);
	}

	draw { | win, name, conductor|
		gui.draw (win, name, conductor)
	}
	
	
// play/stop/pause.resume
	stop {
		player.stop;
 	}
	
	play { 
		player.play;		
	}
 

	pause { 
		player.pause; 
	}

	resume { 	
		player.resume; 
	}

	name_ { | name | player.name_(name) }
	
	name { ^player.name }
	
//player interface

	add { | object |
		player.add(object)
	}
	
	action_ { | playFunc, stopFunc, pauseFunc, resumeFunc |
		this.add ( ActionPlayer(playFunc, stopFunc, pauseFunc, resumeFunc ) )
	}

	buffer_ { | ev| 
		ev.parent = CVEvent.bufferEvent;
		this.add(ev);
	}
	
	controlBus_ { | ev, cvs|
		ev[\cvs] = cvs;
		ev.parent = CVEvent.controlBusEvent;
		this.add(ev)
	}

	synth_ { | ev, cvs|
		ev[\cvs] = cvs;
		ev.parent = CVEvent.synthEvent;
		this.add(ev)
	}

	synthDef_ { | function, cvs, ev|
		var name;
		name = function.hash.asString;
		SynthDef(name, function).store;
		ev = ev ? ();
		ev	.put(\instrument, name)
			.put(\cvs, cvs);
		ev.parent_(CVEvent.synthEvent);
		this.add(ev);
		^ev
	}

	group_ { | ev, cvs|
		ev[\cvs] = cvs;
		ev.parent = CVEvent.groupEvent;
		this.add(ev)
	}
	
	task_ { |func, clock, quant|
		this.add(TaskPlayer(func,clock, quant));
	}
	
	pattern_ { | pat, clock, event, quant |
		this.add(PatternPlayer(pat, clock, event, quant) )
	}

		
}
