package com.skillmirror.skills;

import java.util.List;
import java.util.UUID;

public final class SkillDtos {

    private SkillDtos() {}

    public record SkillOut(UUID id, String name, String type, UUID domainId) {}

    public record DomainOut(UUID id, String name, String description, List<SkillOut> skills) {}

    public record RoleOut(UUID id, String name, String description, UUID domainId) {}

    public record RoleSkillOut(UUID skillId, String name, String type, boolean required) {}

    public record RoleDetail(UUID id, String name, String description,
                             List<RoleSkillOut> skills) {}
}