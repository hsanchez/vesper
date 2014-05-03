package edu.ucsc.refactor.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import edu.ucsc.refactor.Context;
import edu.ucsc.refactor.Location;
import edu.ucsc.refactor.NamedLocation;
import edu.ucsc.refactor.internal.util.AstUtil;
import edu.ucsc.refactor.internal.visitors.SelectedStatementNodesVisitor;
import edu.ucsc.refactor.util.Locations;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import java.util.List;

/**
 * This represents a method of a class.
 *
 * @author hsanchez@cs.ucsc.edu (Huascar A. Sanchez)
 */
public class MethodUnit extends AbstractProgramUnit {
    /**
     * Construct a new {@code Method} program unit.
     *
     * @param name The method's name
     */
    public MethodUnit(String name){
        super(name);
    }

    @Override public List<NamedLocation> getLocations(Context context) {
        Preconditions.checkNotNull(context);

        final List<NamedLocation> namedLocations = Lists.newArrayList();
        final List<Location> instances = Locations.locateWord(context.getSource(), getName());
        for(Location each : instances){

            final SelectedStatementNodesVisitor statements = new SelectedStatementNodesVisitor(
                    each,
                    true
            );

            context.accept(statements);
            statements.checkIfSelectionCoversValidStatements();

            if(!statements.isSelectionCoveringValidStatements()){ return namedLocations; }

            // Note: once formatted, it is hard to locate a method. This mean that statements getSelectedNodes
            // is empty, and the only non null node is the statements.lastCoveringNode, which can be A BLOCK
            // if method is the selection. Therefore, I should get the parent of this block to get the method
            // or class to remove.
            for(ASTNode eachNode : statements.getSelectedNodes()){
                // ignore instance creation, parameter passing,... just give me its declaration

                final MethodDeclaration methodDeclaration = AstUtil.parent(
                        MethodDeclaration.class,
                        eachNode
                );

                if(methodDeclaration != null){
                    if(!AstUtil.contains(namedLocations, methodDeclaration) && getName().equals(methodDeclaration.getName().getIdentifier())){
                        namedLocations.add(new ProgramUnitLocation(methodDeclaration, each));
                    }
                }

            }
        }

        return namedLocations;
    }
}
